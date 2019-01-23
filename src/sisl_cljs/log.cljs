(ns sisl-cljs.log
  (:require
   [goog.Uri]
   [ajax.core :refer [GET POST]]
   [cljs.core.async :refer [chan <! >! put! close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn console [& message]
  (.log js/console (apply str message)))

(defn alert [& message]
  (js/alert (apply str message)))


;; :key_correct
;; :key_incorrect
;; :cue_appear
;; :cue_disappear
;; :cue_enter_zone
;; :cue_middle_zone
;; :cue_exit_zone
;; :dialog_response
;; :speed_change
;; :pause

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

(defn- timestamp [event]
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
             :trial_row_id 0)))

(defn pause-event [value]
  (-> event-log-template
      timestamp
      (assoc :event_type :pause
             :event_value value
             :trial_row_id 0)))

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

;; Event log debugging
(def verbose-debug false)

(def interesting-event-type
  #{:key_correct :key_incorrect})

(def interesting-event-keys
  #{:event_type :event_value})

(defn- interesting-event-filter
  [{:keys [event_type event_value] :as event}]
  (interesting-event-type event_type))

(defn- event-debug-log [event]
  (when (and verbose-debug
             (interesting-event-filter event))
    (console "Event: "
             (select-keys event interesting-event-keys))))


(defn record-event [event]
  (event-debug-log event)
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

(defn- wrap-params [params channel reason]
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

(defn- try-request
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

(defn- new-session [participant scenario]
  (let [url (new goog.Uri (.-URL js/document))]
    (try-request
     new-session-url
     {:params {:session
               {:browser_info (.-userAgent js/navigator)
                :scenario (str scenario)
                :scenario-name (:scenario-name scenario)
                :participant participant}}
      :reason "Could not start new session."})))

(defn- send-to-server [participant session-id queue]
  (try-request
   log-upload-url
   {:params {:participant participant
             :session session-id
             :data queue}
    :reason "Could not upload log chunk."}))

(defn- finish-session [participant session-id]
  (try-request
   finish-session-url
   {:params {:session session-id}
    :reason "Could not finish log session."}))

(defn- logging-loop [participant session-id channel]
  (go-loop [queue #queue []]
    (if-let [event (<! channel)]
      (let [bigger-queue (conj queue event)
            queue-size (count bigger-queue)]
        (if (>= queue-size when-to-send)
          (let [{:keys [success] :as server-response}
                (<! (send-to-server participant session-id bigger-queue))]
            (if success
              (recur #queue [])
              server-response))
          (recur bigger-queue)))
      (let [{:keys [success] :as server-response}
            (<! (send-to-server participant session-id queue))]
        (if success
          (<! (finish-session participant session-id))
          server-response)))))

(defn start-logging [participant scenario]
  (reset! event-channel (chan))
  (go
    (let [{:keys [session-id] :as response}
          (<! (new-session participant scenario))]
      (if session-id
        (<! (logging-loop participant session-id @event-channel))
        response))))

(defn stop-logging []
  (close! @event-channel))
