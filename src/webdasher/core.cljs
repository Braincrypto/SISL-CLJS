(ns ^:figwheel-always webdasher.core
    (:require
     [reagent.core :as reagent :refer [atom]]
     [webdasher.render :as render]
     [webdasher.settings :as settings]
     [webdasher.input :as input]
     [webdasher.cue :as cue]
     [webdasher.dialog :as dialog]
     [webdasher.score :as score]
     [webdasher.log :as log]))

(enable-console-print!)

(def starting-app-state
  {:trial []
   :cues []
   :scored-cues '()
   :keys-down #{}
   :score {:hits 0 :misses 0 :streak 0}
   :accumulator 0.0
   :speed 1.0
   :status :stopped
   :current-time nil})

(defn fresh-state []
  (assoc starting-app-state
         :trial @settings/fresh-trial
         :speed (get-in @settings/scenario [:speed :default])))

(defonce app-state (atom starting-app-state))
(defonce animation-frame-request (atom nil))

(defn process-event [state event]
  (case (:type event)
    "cue" (cue/process-cue state event)
    "dialog" (dialog/process-dialog state event)
    state))

(defn process-trial-events [state]
  (let [{:keys [accumulator trial]} state
        next-event (first trial)
        time-balance (- accumulator (:appear-time-ms next-event))]
    (if (> time-balance 0)
      (-> state
          (assoc :accumulator time-balance
                 :trial (rest trial))
          (process-event next-event))
      state)))

(defn check-finished
  "The game is finished when there are:
    * No cues on screen.
    * No events in queue.
    * At least 2500ms has passed since the last event was processed."
  [state]
  (let [{:keys [cues trial accumulator status]} state
        finished (and (empty? cues)
                      (empty? trial)
                      (> accumulator 2500))
        new-status (if (and finished (= status :running))
                     :finished
                     status)]
    (assoc state :status new-status)))

(defn time-update [timestamp state]
  (let [delta-time (* (:speed state) (- timestamp (:current-time state)))
        accumulator (+ delta-time (:accumulator state))]
    (-> state
        (assoc
         :current-time timestamp
         :delta-time delta-time
         :accumulator accumulator)
        cue/update-cues
        cue/remove-missed-cues
        process-trial-events
        score/update-speed
        check-finished)))

(declare request-animation)
(defn time-loop [time]
  (let [{status :status :as new-state}
        (swap! app-state (partial time-update time))]
    (case status
      :running (request-animation)
      :finished (log/stop-logging)
      true)))

(defn request-animation []
  (reset! animation-frame-request
          (.requestAnimationFrame js/window time-loop)))

(defn start-animation [new-status]
  (swap! app-state
         #(assoc %
                 :current-time (.now js/performance)
                 :status new-status))
  (request-animation))

(defn cancel-animation []
  (when @animation-frame-request
    (.cancelAnimationFrame js/window @animation-frame-request)
    (reset! animation-frame-request nil)))

(defn new-game []
  (log/start-logging)
  (reset! app-state (fresh-state))
  (start-animation :running))

(defn reset-game []
  (cancel-animation)
  (log/stop-logging)
  (reset! app-state (fresh-state)))

(reagent/render-component [render/render-page app-state]
                          (. js/document (getElementById "app")))


(input/setup-input-handlers app-state)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
