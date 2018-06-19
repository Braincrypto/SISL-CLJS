(ns sisl-cljs.keyboard
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [sisl-cljs.audio :as audio]
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

(defn hit? [event
            {:keys [value scored] :as cue}]
  (if scored
    :already-scored
    (if (and (= value (:lane event))
             (cue/in-target? cue))
      :hits
      :misses)))

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
        (log/record-cue :key_correct hit speed))
      (assoc-in state [:current-event :hit] true))))

(defn audio-trigger [state]
  (let [{:keys [sound-mode lane-sounds]} @scenario
        {:keys [current-event]} state
        {:keys [lane hit]} current-event
        correct-sound (lane-sounds lane)]
    (case sound-mode
      "key"
      (audio/play correct-sound)

      "cue"
      (if hit
        (audio/play correct-sound)
        (audio/play audio/incorrect-sound))

      true))
  state)

(defn hit-cues
  [{:keys [current-event status cues scored-cues] :as state}]
  (if (and (= (:state current-event) :down)
           (= status :running))
    (let [{:keys [hits misses already-scored]}
          (group-by (partial hit? current-event) cues)

          [first-missed & rest-missed] (sort-by :top > misses)
          hit-cues (map #(assoc % :scored 1) hits)

          missed? (and first-missed (empty? hit-cues))

          missed-cues (if missed?
                        [(assoc first-missed :scored 0)]
                        [])

          other-cues (concat already-scored (when-not missed? [first-missed]) rest-missed)]
      (-> state
          (score/update-score hit-cues missed-cues)
          (record-hits hit-cues first-missed)
          audio-trigger
          (assoc :cues (concat hit-cues missed-cues other-cues)
                 :scored-cues (concat scored-cues hit-cues missed-cues))))
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
