(ns webdasher.score
  (:require
   [webdasher.log :as log]
   [webdasher.settings :as settings]))

(defn update-score [state hits misses]
  (let [{old-hits :hits
         old-misses :misses
         old-streak :streak
         :as old-score} (:score state)
        hit-count (count hits)
        miss-count (count (filter (complement :missed) misses))]
    (assoc state :score
           (cond (> hit-count 0)
                 {:hits (+ old-hits hit-count)
                  :misses old-misses
                  :streak (+ old-streak hit-count)}

                 (> miss-count 0)
                 {:hits old-hits
                  :misses (+ old-misses miss-count)
                  :streak 0}

                 true old-score))))

(defn record-speed [new-speed]
  (log/record-event (log/speed-event new-speed))
  new-speed)

(defn calculate-speed
  [current-speed score-window
   {:keys [down-threshold up-threshold
           numerator denominator
           min-speed max-speed]}]
  (let [hit-count (count (remove :missed score-window))]
    (cond
      (>= hit-count up-threshold)
      (record-speed (min (* current-speed (/ numerator denominator)) max-speed))

      (<= hit-count down-threshold)
      (record-speed (max (* current-speed (/ denominator numerator)) min-speed))

      true
      current-speed)))

(defn update-speed [state]
  (let [{:keys [speed scored-cues]} state
        {:keys [lookback] :as params} (:speed @settings/scenario)]
    (if (>= (count scored-cues) lookback)
      (assoc state
             :speed (calculate-speed speed (take lookback scored-cues) params)
             :scored-cues (drop lookback scored-cues))
      state)))
