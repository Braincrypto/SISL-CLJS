(ns sisl-cljs.keyboard
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

(defn randomize-hit [hit]
  (let [{:keys [flip probability bias]} (:random-feedback @scenario)
        random (< (rand) probability)
        biased (< (rand) bias)]
    (if random
      (if flip
        (if biased
          (not hit)
          hit)
        biased)
      hit)))

(defn update-keys-down
  [{{key :character new-state :state hit :hit} :current-event
    keys-down :keys-down
    :as state}]
  (if (= :down new-state)
    (assoc-in state [:keys-down key] (randomize-hit hit))
    (assoc state :keys-down (dissoc keys-down key))))

(defn hit? [event cue]
  (if (and (= (:value cue) (:lane event))
           (cue/in-target? cue)
           (not (:missed cue)))
    :hit
    :miss))

(defn record-response
  [{:keys [current-event keys-down] :as state}]
  (log/record-key current-event keys-down)
  state)

(defn record-hits
  [{:keys [speed current-event] :as state} hit-cues missed-cue]
  (if (empty? hit-cues)
    (do
      (when missed-cue
        (log/record-cue :key_incorrect missed-cue speed))
      state)
    (do
      (doseq [hit hit-cues]
        (log/record-cue :key_correct hit speed)
        (log/record-cue :cue_disappear hit speed))
      (assoc-in state [:current-event :hit] true))))


(defn hit-cues
  [{:keys [current-event status cues scored-cues] :as state}]
  (if (and (= (:state current-event) :down)
           (= status :running))
    (let [{hit-cues :hit misses :miss}
          (group-by (partial hit? current-event) cues)

          [first-missed & rest-missed] (sort-by :top > misses)

          remaining (if (and first-missed
                             (empty? hit-cues))
                      (conj rest-missed (assoc first-missed :missed true))
                      misses)]
      (-> state
          (score/update-score hit-cues [first-missed])
          (record-hits hit-cues first-missed)
          (assoc :cues remaining
                 :scored-cues (concat scored-cues hit-cues))))
    state))

(defn process-key-event [state event]
  (-> state
      (assoc :current-event event)
      record-response
      hit-cues
      update-keys-down
      (dissoc :current-event)))

(defn key-lane [state c]
  ((@scenario :key-map) c))

(defn key-handler [state transition e]
  (let [repeat (.-repeat (.getBrowserEvent e))
        char (event-to-char e)
        lane (key-lane state char)
        event {:state transition :character char :lane lane
               :hit false}]
    (if (and lane
             (not repeat)
             (not= (:status @state) :paused))
      (swap! state process-key-event event))))

(defn setup-input-handlers [state]
  (events/listen (dom/getWindow) "keydown" #(key-handler state :down %))
  (events/listen (dom/getWindow) "keyup" #(key-handler state :up %)))
