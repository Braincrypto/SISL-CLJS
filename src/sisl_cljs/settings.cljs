(ns sisl-cljs.settings
  (:require
   [ajax.core :refer [GET POST]]
   [cljs.core.async :refer [put!]]
   [reagent.core :as reagent :refer [atom]]
   [clojure.string :refer [split-lines split]]
   [sisl-cljs.log :as log]))

(defonce request-counter (atom 1))
(defonce fresh-trial (atom nil))
(defonce scenario (atom nil))

(defn try-int [val]
  (let [int-val (js/parseInt val)]
    (if (js/isNaN int-val)
      val
      int-val)))

(def trial-row-labels [:cue-row-id :type :value :appear-time-ms :time-to-targ-ms :category])

(defn parse-trial-row [row]
  (->> row
       (#(split % ","))
       (map try-int)
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
                lane-keys]} scenario
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
        lane-gap (/ (- board-width (* lane-count lane-width))
                    (dec lane-count))]
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
           :lane-gap lane-gap)))

(defn parse-scenario [chan response]
  (->> response
       calculate-parameters
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