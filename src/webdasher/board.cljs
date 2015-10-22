(ns webdasher.board
  (:require
   [webdasher.cue :as cue]
   [webdasher.settings :refer [scenario]]))

(def lane-colors
  [
   {:hue 0   :saturation 1.0 :value 1.0 } ;red
   {:hue 120 :saturation 1.0 :value 1.0 } ;green
   {:hue 240 :saturation 1.0 :value 1.0 } ;blue
   {:hue 60  :saturation 1.0 :value 1.0 } ;yellow
   {:hue 180 :saturation 1.0 :value 1.0 } ;cyan
   {:hue 300 :saturation 1.0 :value 1.0 } ;magenta
   ])

(defn to-hsl [color]
  (let [{h :hue sat :saturation val :value} color
        l (* (- 2 sat) (/ val 2))
        s (cond
            (= l 1) 0
            (< l 0.5) (/ (* sat val) (* l 2))
            true (/ (* sat val) (- 2 (* l 2))))]
    (str "hsl(" h "," (* 100 s) "%," (* 100 l) "%)")))

(def lane-colors-hsl
  (map to-hsl lane-colors))

(defn cue-color [cue]
  (cond (:missed cue) "grey"
        true (nth lane-colors-hsl (:value cue))))

(defn render-target [state lane]
  (let [key (nth (@scenario :lane-keys) lane)
        highlighted ((@state :keys-down) key)
        class (if highlighted "highlighted" "")]
    [:div.target {:style {:height (@scenario :target-height)} :class class}
     [:span.prompt {:style {:width (@scenario :lane-width)}} key]]))


(defn render-cue [cue]
  [:div.cue {:style {:top (:top cue)
                     :left (@scenario :cue-left)
                     :width (@scenario :cue-width)
                     :height (@scenario :cue-width)
                     :background (cue-color cue)}}])

(defn lane-cues [state lane]
  (filter #(= lane (:value %)) (:cues @state)))

(defn lane-pos [lane]
  (* lane (+ (@scenario :lane-gap) (@scenario :lane-width))))

(defn render-lane [state lane]
  [:div.lane {:style {:width (@scenario :lane-width)
                      :height (@scenario :lane-height)
                      :left (lane-pos lane)}}
   [render-target state lane]
   (for [{row-id :cue-row-id :as cue} (lane-cues state lane)]
     ^{:key row-id}
     [render-cue cue])])

(defn render-lanes [state]
  [:div.lanes
   (for [lane (range (@scenario :lane-count))]
     ^{:key lane}
     [render-lane state lane])])

(defn render-score [state]
  (let [{:keys [hits misses streak]} (:score @state)]
    [:ul.score
     [:li.hits "Hits: " hits]
     [:li.misses "Misses: " misses]
     [:li.streak "Streak: " streak]]))

(defn render-board [state]
  [:div.board 
   [render-lanes state]
   [render-score state]])

