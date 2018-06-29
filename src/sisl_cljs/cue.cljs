(ns sisl-cljs.cue
  (:require
   [sisl-cljs.settings :refer [scenario]]
   [sisl-cljs.audio :as audio]
   [sisl-cljs.score :as score]
   [sisl-cljs.log :as log]))

(defn velocity [cue]
  (/ (@scenario :middle-of-zone)
     (cue :time-to-targ-ms)))

(defn spawn [event accumulator]
  (let [v (velocity event)
        top (* accumulator v)]
    (assoc event
           :top top
           :velocity v)))

(defn audio-trigger [speed old-cue new-target-offset]
  (let [{:keys [sound-mode lane-sounds]} @scenario
        {:keys [velocity target-offset value audio-offset]} old-cue
        sound (lane-sounds value)]
    (when (not= audio-offset -1)
      (case sound-mode
        "space"
        (when (and (> target-offset audio-offset)
                   (>= audio-offset new-target-offset))
          (audio/play sound))

        "time"
        (let [audio-distance (* velocity audio-offset)]
          (when (and (> target-offset audio-distance)
                     (>= audio-distance new-target-offset))
            (audio/play sound)))

        true))))

(defn fall [dt speed cue]
  (let [prev-top (:top cue)
        new-top (+ prev-top (* (:velocity cue) dt))
        target-offset (int (- (@scenario :middle-of-zone) new-top))]
    (audio-trigger speed cue target-offset)
    (assoc cue
           :prev-top prev-top
           :top new-top
           :target-offset target-offset)))

(defn color [{:keys [value scored] :as cue}]
  (if (= scored 0)
    "grey"
    ((@scenario :lane-colors) value)))

(defn visible? [cue]
  (not (= (:scored cue) 1)))

(defn hit-bottom? [cue]
  (> (:top cue) (- (@scenario :board-height) (@scenario :cue-height))))

(defn in-target? [cue]
  (> (@scenario :bottom-of-zone) (:top cue) (@scenario :top-of-zone)))

(defn crossed-border? [cue border]
  (let [{top :top prev-top :prev-top} cue]
    (> top border prev-top)))

(def borders
  {:cue_enter_zone :top-of-zone
   :cue_middle_zone :middle-of-zone
   :cue_exit_zone :bottom-of-zone})

(defn record-border-crossings [state cues]
  (doseq [cue cues
          [event-name border] borders]
    (if (crossed-border? cue (@scenario border))
        (log/record-cue event-name cue (:speed state)))))

(defn remove-scored-cues
  [{:keys [cues scored-cues speed] :as state}]
  (let [{bottom-cues true remaining-cues false} (group-by hit-bottom? cues)
        missed-cues (remove :scored bottom-cues)
        missed (map #(assoc % :scored 0) missed-cues)]
    (doseq [cue missed]
      (log/record-cue :cue_disappear cue speed))
    (-> state
        (assoc :cues remaining-cues
               :scored-cues (concat scored-cues missed))
        (score/update-score [] missed-cues))))

(defn update-cues [{:keys [delta-time speed cues] :as state}]
  (let [updated-cues (map (partial fall delta-time speed) cues)]
    (record-border-crossings state updated-cues)
    (assoc state :cues updated-cues)))

(defn process-cue [state event]
  (let [cue (spawn event (:accumulator state))]
    (log/record-cue :cue_appear cue (:speed state))
    (assoc state
           :cues (conj (:cues state) cue))))
