(ns ^:figwheel-always sisl-cljs.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [cljs.core.async :refer [chan timeout <!]]
   [goog.dom :as dom]
   [goog.events :as events]
   [sisl-cljs.render :as render]
   [sisl-cljs.settings :as settings]
   [sisl-cljs.keyboard :as keyboard]
   [sisl-cljs.mouse :as mouse]
   [sisl-cljs.cue :as cue]
   [sisl-cljs.dialog :as dialog]
   [sisl-cljs.score :as score]
   [sisl-cljs.log :as log])
  (:require-macros
   [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(def starting-app-state
  {:trial '()
   :cues []

   :status :stopped
   :current-time nil
   :accumulator 0.0

   :input-mode :keyboard
   :keys-down {}

   :score {:hits 0 :misses 0 :streak 0}
   :score-updating true

   :scored-cues '()
   :speed 1.0
   :speed-updating true})

(defn fresh-state []
  (assoc starting-app-state
         :trial @settings/fresh-trial
         :speed (get-in @settings/scenario [:speed :default])))

(defonce app-state (atom starting-app-state))
(defonce animation-frame-request (atom nil))

(defn process-event [state event]
  ((case (:type event)
     "cue" cue/process-cue
     "dialog" dialog/process-dialog
     "speed" score/process-speed-event
     "score" score/process-score-event
     "scenario" settings/process-scenario-event
     (fn [state event] state))
   state event))

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
        cue/remove-scored-cues
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

(defn pause-game []
  (log/record-event (log/pause-event 0))
  (cancel-animation)
  (swap! app-state
         #(assoc % :status :paused)))

(defn resume-game []
  (when (= (:status @app-state) :paused)
    (log/record-event (log/pause-event 1))
    (start-animation :running)))

(defn run-logging [participant]
  (go
    (let [{:keys [success code] :as log-result}
          (<! (log/start-logging participant @settings/scenario))]
      (swap! app-state #(assoc % :log-result log-result)))))

(defn new-game []
  (go
    (let [settings-channel (chan)
          scenario-id (js/prompt "Trial ID")
          participant (js/prompt "Participant ID")]
      ;; Wait for the success events to come back on the settings channel
      (settings/load-settings settings-channel scenario-id)
      (<! settings-channel)
      (<! settings-channel)

      (reset! app-state (assoc (fresh-state)
                               :participant participant))
      (run-logging participant)
      (start-animation :running))))

(defn reset-game []
  (cancel-animation)
  (log/stop-logging)
  (settings/reset-scenario!)
  (reset! app-state (fresh-state)))

(def debug-methods
  {:start-animation start-animation
   :new-game new-game
   :reset-game reset-game
   :pause-game pause-game
   :resume-game resume-game})

(reagent/render-component [render/render-page app-state debug-methods]
                          (. js/document (getElementById "app")))

(defn blur-handler [event]
  (if (and (= :running (:status @app-state))
           (not (:debug @settings/scenario)))
    (pause-game)))

(defn setup-focus-handlers []
  (events/listen (dom/getWindow) "blur" blur-handler))

(defonce initial-setup
  (let [settings-channel (chan)]
    (events/removeAll (dom/getWindow))
    (settings/load-settings settings-channel "default")
    (go
      (log/console (<! settings-channel))
      (log/console (<! settings-channel))

      ;; We need a brief timeout here so Reagent can
      ;; receive the r/atom callbacks from trial and scenario
      ;; and render the board so mouse/setup-input-handlers will
      ;; have a "board" element to target.
      (<! (timeout 1))

      (setup-focus-handlers)
      (keyboard/setup-input-handlers app-state)
      (mouse/setup-input-handlers app-state)

      (if-not (:debug @settings/scenario)
        (new-game)))))
