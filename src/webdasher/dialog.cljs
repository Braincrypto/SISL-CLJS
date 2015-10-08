(ns webdasher.dialog
  (:require
   [reagent.core :as r]
   [webdasher.log :as log]
   [webdasher.settings :as settings]))

(defonce selection (r/atom nil))

(defn process-dialog [state event]
  (reset! selection nil)
  (assoc state
         :status :dialog
         :dialog (keyword (event :value))))

(defn complete-dialog [dialog]
  (log/record-event (log/dialog-response (or @selection -1)))
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
       option]])])

(defn render-dialog [state]
  (let [{:keys [title type text button options :as dialog]}
        (get-in @settings/scenario [:dialog (:dialog @state)])

        rating (= type "rating")]
    [:div.dialog
     [:h1 title]
     [:div.dialog_text text]
     [:p "Selection: " @selection]
     (if rating
         [render-ratings options selection])
     [:input {:type "button"
              :disabled (and rating (not @selection))
              :value button
              :on-click #(complete-dialog dialog)}]]))
