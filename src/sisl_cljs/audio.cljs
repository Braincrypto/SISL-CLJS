(ns sisl-cljs.audio
  (:require
   [cljs-bach.synthesis :refer [add
                                sawtooth
                                white-noise
                                triangle
                                audio-context
                                connect->
                                square
                                percussive
                                sine
                                gain
                                run-with
                                current-time
                                destination]]))

(defonce context (audio-context))

(defn buzzer [freq]
  (connect->
   (add #_white-noise
        (triangle freq)
        (square (* freq 1.1))
        (sine (* freq 0.9)))
   (percussive 0.01 0.3)
   (gain 0.05)
   destination))

(def incorrect-sound (buzzer 100))

(defn ping [freq]
  (connect->
   (square freq)
   (percussive 0.001 0.2)
   (gain 0.05)
   destination))

(defn play [synth]
  (synth context (+ 0.01 (current-time context)) 0.00))

(def warmup-sound
  (connect->
   (square 100)
   (gain 0.0001)
   destination))

(defn warmup []
  (warmup-sound context (+ 0.01 (current-time context)) 0.00))

(defonce keep-warm
  (.setInterval js/window warmup 1000))
