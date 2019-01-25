(ns sisl-cljs.settings
  (:require
   [ajax.core :refer [GET POST]]
   [cljs.core.async :refer [put!]]
   [reagent.core :as reagent :refer [atom]]
   [clojure.string :refer [split-lines split]]
   [sisl-cljs.audio :as audio]
   [sisl-cljs.log :as log]))

(defonce request-counter (atom 1))
(defonce fresh-trial (atom nil))
(defonce fresh-scenario (atom nil))
(defonce scenario (atom nil))

(def defaults
  {
   ;; If running in debug, there will be a row of buttons displayed below the
   ;; play field, used for pausing or resetting the trial.
   :debug true

   ;; How tall the playing field is, in pixels.
   :board-height 600

   ;; How wide the playing field is, in pixels.
   :board-width 300

   ;; How wide each lane is, in pixels.
   :lane-width 60

   ;; The gap between the bottom of the playing field and the target zone, in pixels.
   :bottom-gap 50

   ;; How wide a cue is, expressed as a multiple of the width of a lane.
   :cue-width-multiplier 0.8
   ;; How tall a cue is, expressed as a multiple of the width of a lane.
   :cue-height-multiplier 0.8

   ;; How tall the target zone is, expressed as a multiple of the width of a lane.
   :target-height-multiplier 1.2

   ;; Which keys are used to respond for each lane. If you want to add more lanes,
   ;; just add more lane keys.
   :lane-keys ["D" "F" "J" "K"]

   ;; Possible sound modes:
   ;; time - Sound is played with time-based offset of cue crossing middle of zone
   ;; space - Sound is played with space-based offset of cue crossing middle of zone
   ;; cue - Sound is played on keystroke, corresponding with correctness.
   ;;       If an incorrect input is made, an error tone will play.
   ;; key - Sound is played on keystroke, regardless of correctness
   :sound-mode ""

   ;; The pitches of the sounds made by each lane, in Hz
   :lane-pitches [261.63 293.66 329.63 349.23]

   ;; Parameters governing the automatic adjustment of speed.
   ;; Every time a cue is scored as either a hit or a miss,
   ;; it is added into a "lookback" list. When that list is equal
   ;; to the "lookback" parameter in size, speed is adjusted
   ;; by the following algorithm (implemented in calculate-speed in
   ;; score.cljs) and the lookback list is cleared out.
   ;;
   ;; 1. Calculate the number of correct responses in the lookback window.
   ;; 2. If that number is greater than or equal to up-threshold, increase speed.
   ;; 3. If it is lesser than or equal to down-threshold, decrease speed.
   ;; 4. Otherwise, speed remains the same.
   ;;
   ;; When speed is increased, the current speed is multiplied by
   ;; (numerator / denominator). When speed is decreased, it is multiplied
   ;; by (denominator / numerator). It is capped on either end by max-speed
   ;; and min-speed.
   :speed {
           ;; The default speed multiplier.
           :default 1.0
           :lookback 12
           :down-threshold 6
           :up-threshold 9
           :numerator 21.0
           :denominator 20.0
           :max-speed 500.0
           :min-speed 0.5}

   ;; The colors of each lane, specified in the HSV color space.
   ;; If there are more lanes than colors, excess colors are unused.
   :colors [{:hue 0   :saturation 1.0 :value 1.0} ;red
            {:hue 120 :saturation 1.0 :value 1.0} ;green
            {:hue 240 :saturation 1.0 :value 1.0} ;blue
            {:hue 60  :saturation 1.0 :value 1.0} ;yellow
            {:hue 180 :saturation 1.0 :value 1.0} ;cyan
            {:hue 300 :saturation 1.0 :value 1.0}] ;magenta

   ;; An experimental (and possibly buggy?) feature to test what happens
   ;; when the player is given feedback that does not correspond to actual
   ;; input.
   :random-feedback {
                     ;; The probability that a given feedback will be altered.
                     :probability 0.0

                     ;; If true, feedback will be flipped with a probability equal
                     ;; to the 'bias' value. If false, random feedback will be 'hit'
                     ;; with a probability equal to the bias value and 'miss' otherwise.
                     :flip false
                     :bias 0.5}})


(defn to-hsl [color]
  (let [{h :hue sat :saturation val :value} color
        l (* (- 2 sat) (/ val 2))
        s (cond
            (= l 1) 0
            (< l 0.5) (/ (* sat val) (* l 2))
            true (/ (* sat val) (- 2 (* l 2))))]
    (str "hsl(" h "," (* 100 s) "%," (* 100 l) "%)")))

(defn parse-value [val]
  (if (js/isNaN val)
    val
    (if (.includes val ".")
      (js/parseFloat val)
      (js/parseInt val))))

(def trial-row-labels [:cue-row-id :type :value :appear-time-ms :time-to-targ-ms :category :audio-offset])

(defn parse-trial-row [row]
  (->> row
       (#(split % ","))
       (map parse-value)
       (zipmap trial-row-labels)))

(defn parse-trial [chan response]
  (->> response
       split-lines
       rest
       (map parse-trial-row)
       (reset! fresh-trial))
  (put! chan "Trial loaded."))

(defn load-trial [chan id]
  (let [counter (swap! request-counter inc)
        filename (str "trial/" id ".csv?foo=" counter)]
    (GET filename
         {:handler (partial parse-trial chan)
          :error-handler #(log/console "Failed to parse trial file.")})))

;; Scenario parsing
(defn calculate-parameters [scenario]
  (let [{:keys [board-height
                board-width
                lane-width
                bottom-gap
                cue-width-multiplier
                cue-height-multiplier
                target-height-multiplier
                lane-keys
                lane-pitches
                colors]} scenario
        lane-height (- board-height bottom-gap)
        cue-width (* lane-width cue-width-multiplier)
        cue-height (* lane-width cue-height-multiplier)
        cue-left (/ (- lane-width cue-width) 2)
        target-height (* lane-width target-height-multiplier)
        top-of-zone (- board-height bottom-gap target-height cue-height)
        bottom-of-zone (+ top-of-zone target-height cue-height)
        middle-of-zone (/ (+ top-of-zone bottom-of-zone) 2)
        lane-count (count lane-keys)
        key-map (zipmap lane-keys (range))
        lane-sounds (mapv audio/ping lane-pitches)
        lane-gap (/ (- board-width (* lane-count lane-width))
                    (dec lane-count))
        lane-colors (into [] (map to-hsl colors))]
    (assoc scenario
           :lane-height lane-height
           :cue-width cue-width
           :cue-height cue-height
           :cue-left cue-left
           :target-height target-height
           :top-of-zone top-of-zone
           :bottom-of-zone bottom-of-zone
           :middle-of-zone middle-of-zone
           :lane-count lane-count
           :key-map key-map
           :lane-sounds lane-sounds
           :lane-gap lane-gap
           :lane-colors lane-colors)))

(defn parse-scenario [chan id response]
  (->> response
       (merge defaults
              {:scenario-name id})
       calculate-parameters
       (reset! fresh-scenario)
       (reset! scenario))
  (put! chan "Scenario loaded."))

(defn load-scenario [chan id]
  (let [counter (swap! request-counter inc)
        filename (str "scenario/" id ".json?foo=" counter)]
    (GET filename
         {:handler (partial parse-scenario chan id)
          :error-handler #(log/console "Failed to parse scenario file.")
          :response-format :json
          :keywords? true})))

(defn load-settings [chan id]
  (load-trial chan id)
  (load-scenario chan id))

(defn process-scenario-event
  [state {:keys [category value] :as event}]
  (let [key-path (map keyword (split category "."))]
    (swap! scenario
           #(calculate-parameters (assoc-in % key-path value))))
  state)

(defn reset-scenario! []
  (reset! scenario @fresh-scenario))
