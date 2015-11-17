(ns webdasher.score
  (:require
   [webdasher.log :as log]
   [webdasher.settings :as settings]))

(defn update-score
  [{{:keys [hits misses streak] :as score} :score
    score-updating :score-updating :as state}
   hit
   miss]
  (let [hit-count (count hit)
        miss-count (count (filter (complement :missed) miss))]
    (assoc state :score
           (cond
             (not score-updating)
             score

             (> hit-count 0)
             {:hits (+ hits hit-count)
              :misses misses
              :streak (+ streak hit-count)}

             (> miss-count 0)
             {:hits hits
              :misses (+ misses miss-count)
              :streak 0}

             true
             score))))

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

(defn update-speed [{:keys [speed speed-updating scored-cues] :as state}]
  (let [{:keys [lookback] :as params} (:speed @settings/scenario)]
    (if (and speed-updating
             (>= (count scored-cues) lookback))
      (assoc state
             :speed (calculate-speed speed (take lookback scored-cues) params)
             :scored-cues (drop lookback scored-cues))
      state)))

(defn process-speed-event
  [{:keys [speed speed-updating] :as state}
   {:keys [value time-to-targ-ms] :as event}]

  (let [new-speed
        (case value
          -1 (get-in @settings/scenario [:speed :default])
          2 (/ time-to-targ-ms 1000)
          speed)

        updating
        (case value
          0 false
          1 true
          speed-updating)]

    (if-not (= speed new-speed)
      (record-speed new-speed))

    (assoc state
           :speed new-speed
           :speed-updating updating
           :scored-cues '())))

(defn process-score-event [state
                           {:keys [value] :as event}]
  (case value
    -1 (assoc state :score {:hits 0 :misses 0 :streak 0} :scored-cues '())
    0 (assoc state :score-updating false :scored-cues '())
    1 (assoc state :score-updating true :scored-cues '())
    state))
