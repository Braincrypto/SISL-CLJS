(ns webdasher.log
  (:require
   [ajax.core :refer [GET POST]]
   [cljs.core.async :refer [chan <! >! put! close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn console [& message]
  (.log js/console (apply str message)))

(defn alert [& message]
  (js/alert (apply str message)))

(defn log-state [state message]
  (console (str message " " state))
  state)

;; :key_correct
;; :key_incorrect
;; :cue_appear
;; :cue_disappear
;; :cue_enter_zone
;; :cue_middle_zone
;; :cue_exit_zone
;; :dialog_response
;; :speed_change

(def event-log-template
  {
   :type :event
   :date_time nil
   :time_stamp_ms nil
   :trial_row_id nil
   :event_type nil
   :event_value nil
   })

(def input-log-template
  {
   :type :input
   :date_time nil
   :time_stamp_ms nil
   :event_type nil
   :event_value nil
   :key_state nil
   })

(def new-session-url "new-session.php")
(def log-upload-url "upload-data.php")
(def finish-session-url "finish-session.php")

(defonce event-channel (atom (chan)))
(def when-to-send 100)

(defn timestamp [event]
  (assoc event
         :time_stamp_ms (int (. js/performance now))
         :date_time (.toISOString (js/Date.))))

(defn cue-event [event cue]
  (-> event-log-template
      timestamp
      (assoc :event_type event
             :event_value cue
             :trial_row_id (:cue-row-id cue))))

(defn speed-event [new-speed]
  (-> event-log-template
      timestamp
      (assoc :event_type :speed_change
             :event_value new-speed
             :trial_row_id -1)))

(defn dialog-response [row-id response]
  (-> event-log-template
      timestamp
      (assoc :event_type :dialog_response
             :event_value response
             :trial_row_id row-id)))

(defn key-response [event key-state]
  (-> input-log-template
      timestamp
      (assoc :event_type (:state event)
             :event_value (:lane event)
             :key_state key-state)))

(defn record-event [event]
  (put! @event-channel event))

(defn record-cue [event cue speed]
  (record-event
   (cue-event event
              (update cue
                      :velocity * speed))))

(defn record-dialog [row-id response]
  (record-event (dialog-response row-id response)))

(defn record-key [event key-state]
  (record-event (key-response event key-state)))


(defn wrap-params [params channel reason]
  (assoc
   {:handler #(put! channel %)
    :error-handler
    #(put! channel {:success false
                    :reason reason
                    :server-response %})
    :format :json
    :response-format :json
    :keywords? true}
   :params params))

(defn try-request
  [url
   {:keys [params max-tries reason]
    :or {params {}
         max-tries 10
         reason "Request failed."}}]
  (let [server-channel (chan)]
    (go-loop [tries 1]
      (POST url (wrap-params params server-channel reason))
      (let [{:keys [success] :as response} (<! server-channel)]
        (if (or success (= tries max-tries))
          response
          (recur (inc tries)))))))

(defn new-session []
  (try-request
   new-session-url
   {:params {:session
             {:browser_info (.-userAgent js/navigator)
              :machine_info ""
              :turk_user_id ""
              :turk_hit_id ""
              :turk_assignment_id ""}}
    :reason "Could not start new session."}))

(defn send-to-server [session-id queue]
  (try-request
   log-upload-url
   {:params {:session session-id
             :data queue}
    :reason "Could not upload log chunk."}))

(defn finish-session [session-id]
  (try-request
   finish-session-url
   {:params {:session session-id}
    :reason "Could not finish log session."}))

(defn logging-loop [session-id channel]
  (go-loop [queue #queue []]
    (if-let [event (<! channel)]
      (let [bigger-queue (conj queue event)
            queue-size (count bigger-queue)]
        (if (>= queue-size when-to-send)
          (let [{:keys [success] :as server-response}
                (<! (send-to-server session-id bigger-queue))]
            (if success
              (recur #queue [])
              server-response))
          (recur bigger-queue)))
      (let [{:keys [success] :as server-response}
            (<! (send-to-server session-id queue))]
        (if success
          (<! (finish-session session-id))
          server-response)))))

(defn start-logging []
  (reset! event-channel (chan))
  (go
    (let [{:keys [session-id] :as response}
          (<! (new-session))]
      (if session-id
        (<! (logging-loop session-id @event-channel))
        response))))

(defn stop-logging []
  (close! @event-channel))
