(ns sisl-cljs.render
  (:require
   [cljs.core.async :refer [chan]]
   [goog.style :as style]
   [goog.dom :as dom]
   [sisl-cljs.board :as board]
   [sisl-cljs.log :as log]
   [sisl-cljs.dialog :as dialog]
   [sisl-cljs.settings :refer [scenario fresh-trial load-settings]]))

(defn set-font-size [elt size]
  (set! (.. elt -style -fontSize) (str size "px")))

(defn get-element-width [elt]
  (-> elt style/getSize .-width))

(defn scale-completion-code []
  (let [div (dom/getElement "completion_code")
        span (dom/getElement "completion_span")
        target-width (* 0.90 (get-element-width div))]
    (doseq [font-size (range 10 50)
            :while (< (get-element-width span) target-width)]
      (set-font-size span font-size))))

(defn render-completion-code [state]
  (js/setTimeout scale-completion-code 0)
  [:div#completion_code
   [:span#completion_span
    (get-in @state [:log-result :code])]])

(defn render-finished-success [state]
  [:div.dialog
   {:style {:height (@scenario :board-height) :width (@scenario :board-width)}}
   [:h1 "Congratulations!"]
   [:p "You're finished. Thanks for playing!"]
   [:p "Your completion code is"]
   [render-completion-code state]])

(defn render-finished-failure [state]
  [:div.dialog
   {:style {:height (@scenario :board-height) :width (@scenario :board-width)}}
   [:h1 "Oops!"]
   [:p "Something went wrong during play,
and your data was not transmitted to our server.
Unfortunately, this means we cannot supply a
completion code. Please check your internet
connection and try again later."]])

(defn render-finished [state]
  (if (get-in @state [:log-result :success])
    [render-finished-success state]
    [render-finished-failure state]))

(defn render-debug-controls [state controls]
  [:div.debug_controls
   (if (= (:status @state) :stopped)
       [:input {:type "button"
                :value "Start"
                :on-click (controls :new-game)}]
       [:input {:type "button"
                :value "Reset"
                :on-click (controls :reset-game)}])
   (when (= (:status @state) :paused)
     [:input {:type "button"
              :value "Resume"
              :on-click (controls :resume-game)}])
   (when (= (:status @state) :running)
     [:input {:type "button"
              :value "Pause"
              :on-click (controls :pause-game)}])
   [:input {:type "button"
            :value "Reload Settings"
            :on-click #(load-settings (chan) "default")}]])

(def interesting-keys [:status :speed])

(defn should-show-log-warning [state]
  (let [{{success :success :as log-result} :log-result status :status} @state]
    (and (not= status :finished)
         log-result
         (not success))))

(defn render-log-warning [state]
  (if (should-show-log-warning state)
    [:div.log_warning
     [:p
      [:img.error {:src "error.png"}]
      "Oh no! Something has gone wrong with your connection to our logging server. You will not receive credit for this task."]]))

(defn render-debug-info [state]
  [:div.debug_info
   [:div.state (str (select-keys @state interesting-keys))]])

(defn render-debug [state controls]
  (if (@scenario :debug)
    [:div.debug
     [render-debug-controls state controls]
     [render-debug-info state]]))

(defn render-paused [state controls]
  (if (= (:status @state) :paused)
    [:div.log_warning
     [:p
      "We noticed you stepped away for a second so we paused for you."]
     [:input {:type "button"
              :value "Resume"
              :on-click (controls :resume-game)}]]))

(defn render-page [state controls]
  (if (and @scenario @fresh-trial)
    [:div.appcontents
     (case (:status @state)
       :dialog
       [dialog/render-dialog state controls]

       :finished
       [render-finished state]

       [board/render-board state])
     [render-log-warning state]
     [render-paused state controls]
     [render-debug state controls]]
    [:div.appcontents "Loading..."]))

