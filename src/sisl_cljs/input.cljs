(ns sisl-cljs.input
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [sisl-cljs.cue :as cue]
            [sisl-cljs.score :as score]
            [sisl-cljs.log :as log]
            [sisl-cljs.settings :refer [scenario]]))

(defn event-to-char [e]
  (-> e
      .-keyCode
      js/String.fromCharCode))

(defn update-keys-down [state event]
  (let [keys-down (:keys-down state)
        {key :character new-state :state} event
        operation (if (= :down new-state) conj disj)]
    (assoc state :keys-down (operation keys-down key))))

(defn hit? [event cue]
  (if (and (= (:value cue) (:lane event))
           (cue/in-target? cue)
           (not (:missed cue)))
    :hit
    :miss))

(defn record-response [state event]
  (log/record-key event (:keys-down state))
  state)

(defn record-hits [{:keys [speed] :as state} event hit-cues missed-cue]
  (doseq [hit hit-cues]
    (log/record-cue :key_correct hit speed)
    (log/record-cue :cue_disappear hit speed))
  (if missed-cue
      (log/record-cue :key_incorrect missed-cue speed))
  state)

(defn hit-cues [state event]
  (if (and (= (:state event) :down) (= (:status state) :running))
    (let [{hit-cues :hit misses :miss}
          (group-by (partial hit? event) (:cues state))

          [first-missed & rest-missed] (sort-by :top > misses)

          remaining (if (and first-missed
                             (empty? hit-cues))
                      (conj rest-missed (assoc first-missed :missed true))
                      misses)]
      (-> state
          (score/update-score hit-cues [first-missed])
          (record-hits event hit-cues first-missed)
          (assoc :cues remaining
                 :scored-cues (concat (:scored-cues state) hit-cues))))
    state))

(defn process-key-event [state event]
  (-> state
      (update-keys-down event)
      (record-response event)
      (hit-cues event)))

(defn key-lane [state c]
  ((@scenario :key-map) c))

(defn key-handler [state transition e]
  (let [repeat (.-repeat (.getBrowserEvent e))
        char (event-to-char e)
        lane (key-lane state char)
        event {:state transition :character char :lane lane}]
    (if (and lane (not repeat))
      (swap! state process-key-event event))))

(defn setup-input-handlers [state]
  (events/removeAll (dom/getWindow))
  (events/listen (dom/getWindow) "keydown" (partial key-handler state :down))
  (events/listen (dom/getWindow) "keyup" (partial key-handler state :up)))
