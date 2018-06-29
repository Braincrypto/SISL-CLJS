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
  (if (and (= value (:lane event))
           (cue/in-target? cue))
    (if scored
      :already-scored-hit
      :hit)
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

  ;; Only check for hits if we're running and it was a keydown
  (if (and (= (:state current-event) :down)
           (= status :running))

    ;; Sort the cues into those hit by this key and those not hit.
    (let [{:keys [hit miss already-scored-hit]}
          (group-by (partial hit? current-event) cues)

          ;; Mark all of the hit cues as correct
          newly-hit (map #(assoc % :scored 1) hit)

          ;; Find the lowest missed cue
          [first-missed & rest-missed :as all-missed] (sort-by :top > miss)

          ;; Did we hit something?
          hit? (not (empty? newly-hit))

          ;; If there was at least one miss and zero hits, then
          ;; we have a miss
          missed? (and first-missed
                       (not (:scored first-missed))
                       (empty? newly-hit))

          ;; Make a list of newly missed cues
          newly-missed (if missed?
                        [(assoc first-missed :scored 0)]
                        [])]
      (-> state
          ;; Update the score tracker
          (score/update-score newly-hit
                              (if hit?
                                []
                                [first-missed]))

          ;; Handle logging
          (record-hits newly-hit first-missed)

          ;; If we're in an audio mode that needs a sound, play it
          audio-trigger

          ;; Rebuild the state data structure
          (assoc :cues (concat newly-hit
                               newly-missed
                               already-scored-hit
                               (if missed?
                                 rest-missed
                                 all-missed))
                 :scored-cues (concat
                               scored-cues
                               newly-hit
                               newly-missed))))
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
