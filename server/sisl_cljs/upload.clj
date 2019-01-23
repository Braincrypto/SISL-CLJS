(ns sisl-cljs.upload
  (:require [clojure.java.io :as io]
            [clojure.edn :refer [read-string]]
            [ring.util.response :refer [response]]
            [cheshire.core :as cheshire]
            [sisl-cljs.logs :refer [write-log-headers
                                    write-log-data]]))


(defn validate-subject [subnum]
  (if (re-matches #"^[0-9A-Za-z]+$" (str subnum))
    subnum
    "default"))

(defn session-dir [subject session-id]
  (str (System/getProperty "user.dir")
       "/logs/" (validate-subject subject) "/" session-id))

(defn make-session-dir [subject session-id]
  (let [directory (session-dir subject  session-id)]
    (io/make-parents (str directory "/foo.txt"))
    directory))

(defn valid-session-dir [subject session-id]
  (let [dir (session-dir subject session-id)]
    (when (.isDirectory (io/file dir))
      dir)))

(defn new-session [{:keys [params] :as request}]
  (let [{:keys [scenario scenario-name participant
                browser_info]} (:session params)
        session-id (java.util.UUID/randomUUID)
        participant (validate-subject participant)
        session-dir (make-session-dir participant session-id)]
    (cheshire/generate-stream
     scenario
     (io/writer (str session-dir "/scenario.json")))

    (cheshire/generate-stream
     {:browser browser_info}
     (io/writer (str session-dir "/system.json")))

    (write-log-headers session-dir)
    
    (response {:success true
               :session-id session-id})))

(defn upload-data [{:keys [params] :as request}]
  (let [{:keys [session participant data]} params
        session-dir (valid-session-dir participant session)]
    (when session-dir
      (write-log-data session-dir data)
      (response {:success true}))))

(defn finish-session [{:keys [params] :as request}]
  (let [{:keys [session participant data]} params
        session-dir (valid-session-dir participant session)
        finish-time (java.time.LocalDateTime/now)]
    (when session-dir
      (cheshire/generate-stream
       {:finish-time (.toString finish-time)}
       (io/writer (str session-dir "/finish.json")))
      (response {:success true
                 :code session}))))
