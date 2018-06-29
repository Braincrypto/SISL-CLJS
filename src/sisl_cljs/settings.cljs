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
   :debug true
   :board-height 600
   :board-width 300
   :lane-width 60
   :bottom-gap 50
   :cue-width-multiplier 0.8
   :cue-height-multiplier 0.8
   :target-height-multiplier 1.2
   :lane-keys ["D" "F" "J" "K"]

   ;; Possible sound modes:
   ;; time - sound is played with time-based offset of cue crossing middle of zone
   ;; space - sound is played with space-based offset of cue crossing middle of zone
   ;; cue - sound is played on keystroke, corresponding with correctness
   ;; key - sound is played on keystroke, regardless of correctness
   :sound-mode ""
   :lane-pitches [261.63 293.66 329.63 349.23]

   :speed {
           :default 1.0
           :lookback 12
           :down-threshold 6
           :up-threshold 9
           :numerator 21.0
           :denominator 20.0
           :max-speed 500.0
           :min-speed 0.5
           }
   :colors [{:hue 0   :saturation 1.0 :value 1.0 } ;red
            {:hue 120 :saturation 1.0 :value 1.0 } ;green
            {:hue 240 :saturation 1.0 :value 1.0 } ;blue
            {:hue 60  :saturation 1.0 :value 1.0 } ;yellow
            {:hue 180 :saturation 1.0 :value 1.0 } ;cyan
            {:hue 300 :saturation 1.0 :value 1.0 }] ;magenta
   :random-feedback {
                     ;; The probability that a given feedback will be altered.
                     :probability 0.0

                     ;; If true, feedback will be flipped with a probability equal
                     ;; to the 'bias' value. If false, random feedback will be 'hit'
                     ;; with a probability equal to the bias value and 'miss' otherwise.
                     :flip false
                     :bias 0.5}
   })

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

(defn load-trial [chan filename]
  (GET filename {:handler (partial parse-trial chan)
                 :error-handler #(log/console "Failed to parse trial file.")}))

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

(defn parse-scenario [chan response]
  (->> response
       (merge defaults)
       calculate-parameters
       (reset! fresh-scenario)
       (reset! scenario))
  (put! chan "Scenario loaded."))

(defn load-scenario [chan filename]
  (GET filename {:handler (partial parse-scenario chan)
                 :error-handler #(log/console "Failed to parse scenario file.")
                 :response-format :json
                 :keywords? true}))

(defn load-settings [chan]
  (let [counter (swap! request-counter inc)
        param (str "?foo=" counter)]
    (load-trial chan (str "trial.csv" param))
    (load-scenario chan (str "scenario.json" param))))

(defn process-scenario-event
  [state {:keys [category value] :as event}]
  (let [key-path (map keyword (split category "."))]
    (swap! scenario
           #(calculate-parameters (assoc-in % key-path value))))
  state)

(defn reset-scenario! []
  (reset! scenario @fresh-scenario))
