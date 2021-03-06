(ns sisl-cljs.dialog
  (:require
   [reagent.core :as r]
   [markdown.core :refer [md->html]]
   [sisl-cljs.log :as log]
   [sisl-cljs.settings :refer [scenario]]))

(def dialog-not-found
  {:type "text"
   :title "Dialog Not Found"
   :text "Oops! It looks like the experimenter requested a dialog
that they didn't provide text for. Sorry about that!"
   :button "I Forgive You"})

(defonce selection (r/atom nil))

(defn process-dialog [state event]
  (reset! selection nil)
  (assoc state
         :status :dialog
         :dialog (keyword (event :value))
         :dialog-row (:cue-row-id event)))

(defn complete-dialog [controls row dialog]
  (log/record-event (log/dialog-response row (or @selection -1)))
  ((controls :start-animation) :running))

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

(defn render-dialog [state controls]
  (let [{:keys [title type text button options :as dialog]}
        (get-in @scenario [:dialog (:dialog @state)] dialog-not-found)

        rating (= type "rating")]
    [:div.dialog
     {:style {:height (@scenario :board-height) :width (@scenario :board-width)}}
     [:h1 title]
     [:div {:dangerouslySetInnerHTML
            {:__html (md->html text)}}]
     (if rating
         [render-ratings options selection])
     [:p [:input {:type "button"
                  :disabled (and rating (not @selection))
                  :value button
                  :on-click #(complete-dialog controls (:dialog-row @state) dialog)}]]]))
