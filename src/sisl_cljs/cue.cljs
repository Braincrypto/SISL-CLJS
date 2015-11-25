(ns sisl-cljs.cue
  (:require
   [sisl-cljs.settings :refer [scenario]]
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

(defn fall [dt cue]
  (let [prev-top (:top cue)
        new-top (+ prev-top (* (:velocity cue) dt))
        target-offset (int (- (@scenario :middle-of-zone) new-top))]
    (assoc cue
           :prev-top prev-top
           :top new-top
           :target_offset target-offset)))

(defn missed? [cue]
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

(defn remove-missed-cues [state]
  (let [{:keys [cues scored-cues speed]} state
        {missed-cues true remaining-cues false} (group-by missed? cues)
        missed (map #(assoc % :missed true) missed-cues)]
    (doseq [cue missed]
      (log/record-cue :cue_disappear cue speed))
    (-> state
        (assoc :cues remaining-cues
               :scored-cues (concat scored-cues missed))
        (score/update-score [] missed-cues))))

(defn update-cues [{:keys [delta-time cues speed] :as state}]
  (let [updated-cues (map (partial fall delta-time) cues)]
    (record-border-crossings state updated-cues)
    (assoc state :cues updated-cues)))

(defn process-cue [state event]
  (let [cue (spawn event (:accumulator state))]
    (log/record-cue :cue_appear cue (:speed state))
    (assoc state
           :cues (conj (:cues state) cue))))
