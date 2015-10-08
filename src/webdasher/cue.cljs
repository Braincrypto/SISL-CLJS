(ns webdasher.cue
  (:require
   [webdasher.settings :refer [scenario]]
   [webdasher.score :as score]
   [webdasher.log :as log]))

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
  (assoc cue
         :prev-top (:top cue)
         :top (+ (:top cue) (* (:velocity cue) dt))))

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

(defn record-border-crossings [cues]
  (doseq [cue cues
          [event-name border] borders]
    (if (crossed-border? cue (@scenario border))
        (log/record-cue event-name cue))))

(defn remove-missed-cues [state]
  (let [{:keys [cues scored-cues]} state
        {missed-cues true remaining-cues false} (group-by missed? cues)
        missed (map #(assoc % :missed true) missed-cues)]
    (doseq [cue missed]
      (log/record-cue :cue_disappear cue))
    (-> state
        (assoc :cues remaining-cues
               :scored-cues (concat scored-cues missed))
        (score/update-score [] missed-cues))))

(defn update-cues [state]
  (let [{dt :delta-time cues :cues} state
        updated-cues (map (partial fall dt) cues)]
    (record-border-crossings updated-cues)
    (assoc state :cues updated-cues)))

(defn process-cue [state event]
  (let [cue (spawn event (:accumulator state))]
    (log/record-cue :cue_appear cue)
    (assoc state
           :cues (conj (:cues state) cue))))
