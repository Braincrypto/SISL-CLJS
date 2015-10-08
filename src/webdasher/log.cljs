(ns webdasher.log
  (:require
   [cljs.core.async :refer [chan <! >! put! close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn console [message]
  (.log js/console message))

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
   :date_time nil
   :time_stamp_ms nil
   :event_type nil
   :event_value nil
   })

(def input-log-template
  {
   :date_time nil
   :time_stamp_ms nil
   :event_type nil
   :event_value nil
   :key_state nil
   })

(defonce event-queue (atom #queue []))
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
             :event_value cue)))

(defn speed-event [new-speed]
  (-> event-log-template
      timestamp
      (assoc :event_type :speed_change
             :event_value new-speed)))

(defn dialog-response [response]
  (-> event-log-template
      timestamp
      (assoc :event_type :dialog_response
             :event_value response)))

(defn key-response [event key-state]
  (-> event-log-template
      timestamp
      (assoc :event_type (:state event)
             :event_value event
             :key_state key-state)))

(defn record-event [event]
  ;; (console (str "Event: " event))
  (put! @event-channel event))

(defn record-cue [event cue]
  (record-event (cue-event event cue)))

(defn record-dialog [response]
  (record-event (dialog-response response)))

(defn record-key [event key-state]
  (record-event (key-response event key-state)))

(defn send-to-server [queue]
  (console (str "Sending " (count queue) " events to server.")))

(defn start-logging []
  (reset! event-channel (chan))
  (go-loop [queue #queue []]
    (reset! event-queue queue)
    (if-let [event (<! @event-channel)]
      (let [bigger-queue (conj queue event)
            queue-size (count bigger-queue)]
        (if (>= queue-size when-to-send)
          (do
            (send-to-server bigger-queue)
            (recur #queue []))
          (recur bigger-queue)))
      (send-to-server queue))))

(defn stop-logging []
  (close! @event-channel))
