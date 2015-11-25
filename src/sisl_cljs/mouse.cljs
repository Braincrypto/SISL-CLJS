(ns sisl-cljs.mouse
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [goog.object]
            [goog.style :as style]
            [sisl-cljs.cue :as cue]
            [sisl-cljs.score :as score]
            [sisl-cljs.log :as log]
            [sisl-cljs.settings :refer [scenario]]))

(defn mouse-handler [state event]
  (if (= (:input-mode state) :mouse)
    (let [board (dom/getElementByClass "board")
          board-left (-> board style/getClientPosition .-x)
          board-width (-> board style/getSize .-width)
          x (- (.-clientX event)
               board-left)
          clamped-x (max 0 (min x board-width))]
      (swap! state assoc :mouse-x clamped-x))))

(defn setup-input-handlers [state]
  (events/listen (dom/getWindow) "mousemove" #(mouse-handler state  %)))
