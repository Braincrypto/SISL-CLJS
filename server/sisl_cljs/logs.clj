(ns sisl-cljs.logs
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def event-headers
  [:date_time :time_stamp_ms :trial_row_id
   :event_type :event_value
   :cue_pos_x :cue_pos_y :cue_vy :cue_target_offset :cue_category])

(def input-headers
  [:date_time :time_stamp_ms
   :event_type :event_value])

(defn write-headers [filename headers]
  (let [header-string (str/join "," (map name headers))]
    (spit filename
          (str header-string "\n"))))
   
(defn write-log-headers
  [session-dir]
  (write-headers (str session-dir "/input.csv") input-headers)
  (write-headers (str session-dir "/event.csv") event-headers))

(def non-cue-event
  #{"dialog_response" "speed_change" "pause"})

(defn fix-fields
  [{:keys [type event_type event_value] :as row}]

  (if (= type "event")
    (if (non-cue-event event_type)
      (assoc row
             :cue_pos_x -1
             :cue_pos_y -1
             :cue_vy -1
             :cue_target_offset -1
             :cue_category nil)
      (let [{:keys [scored value top velocity
                    target-offset category]} event_value]
        (assoc row
               :event_value scored
               :cue_pos_x value
               :cue_pos_y top
               :cue_vy velocity
               :cue_target_offset target-offset
               :cue_category category)))
    row))

(defn transform-row
  [headers row]
  (let [fixed-row (fix-fields row)]
    (map #(fixed-row %) headers)))

(defn write-log-data
  [session-dir data]
  (let [data-by-type (group-by :type data)
        events (data-by-type "event")
        input (data-by-type "input")]
    (with-open [writer (io/writer (str session-dir "/event.csv")
                                  :append true)]
      (csv/write-csv writer (map (partial transform-row event-headers) events)))
    (with-open [writer (io/writer (str session-dir "/input.csv")
                                  :append true)]
      (csv/write-csv writer (map (partial transform-row input-headers) input)))))
