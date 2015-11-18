(ns webdasher.dialog
  (:require
   [reagent.core :as r]
   [webdasher.log :as log]
   [webdasher.settings :refer [scenario]]))

(defonce selection (r/atom nil))

(defn process-dialog [state event]
  (reset! selection nil)
  (assoc state
         :status :dialog
         :dialog (keyword (event :value))
         :dialog-row (:cue-row-id event)))

(defn complete-dialog [row dialog]
  (log/record-event (log/dialog-response row (or @selection -1)))
  (webdasher.core/start-animation :running))

(defn render-ratings [options]
  [:ul.ratings
   (for [[i option] (zipmap (range) options)]
     ^{:key i}
     [:li
      [:label
       [:input {:type "radio"
                :name "rating"
                :value i
                :on-click #(reset! selection i)}]
       [:span.response option]]])])

(defn render-dialog [state]
  (let [{:keys [title type text button options :as dialog]}
        (get-in @scenario [:dialog (:dialog @state)])

        rating (= type "rating")]
    [:div.dialog
     {:style {:height (@scenario :board-height) :width (@scenario :board-width)}}
     [:h1 title]
     [:p text]
     (if rating
         [render-ratings options selection])
     [:p [:input {:type "button"
                  :disabled (and rating (not @selection))
                  :value button
                  :on-click #(complete-dialog (:dialog-row @state) dialog)}]]]))
