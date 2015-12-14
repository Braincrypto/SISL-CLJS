(ns sisl-cljs.board
  (:require
   [sisl-cljs.cue :as cue]
   [sisl-cljs.settings :refer [scenario]]))

(defn cue-color [{:keys [value missed] :as cue}]
  (if missed
    "grey"
    ((@scenario :lane-colors) value)))

(defn render-target [state lane]
  (let [key (nth (@scenario :lane-keys) lane)
        highlighted ((@state :keys-down) key)
        class (case highlighted
                true "highlighted hit"
                false "highlighted miss"
                "")]
    [:div.target {:style {:height (@scenario :target-height)} :class class}
     [:span.prompt {:style {:width (@scenario :lane-width)}} key]]))


(defn render-cue [{:keys [top] :as cue}]
  [:div.cue {:style {:top top
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

(defn render-cursor [state]
  (let [width 30]
    [:div.cursor
     {:style {:position "relative"
              :left (- (:mouse-x @state) (/ (@scenario :lane-width) 2))
              :background "black"

              :top (@scenario :lane-height)
              :width (@scenario :lane-width)
              :height 20}}]))

(defn render-score [state]
  (let [{:keys [hits misses streak]} (:score @state)]
    [:ul.score
     [:li.hits "Hits: " hits]
     [:li.misses "Misses: " misses]
     [:li.streak "Streak: " streak]]))

(defn render-board [state]
  (let [class (if (= :paused (:status @state)) "paused" "")]
    [:div.board
     {:style {:height (@scenario :board-height) :width (@scenario :board-width)}
      :class class}
     [render-lanes state]
     (if (= (:input-mode state) :mouse)
       [render-cursor state])
     [render-score state]]))
