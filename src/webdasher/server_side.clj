(ns webdasher.server-side
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [ring.util.response :refer [response status]]
   [ring.middleware.json :refer [wrap-json-response wrap-json-body]]))

(defonce current-session-id (atom 10000))
(defonce sessions (atom {}))

(defn new-session [{params :body}]
  (let [new-session-id (swap! current-session-id inc)
        session (assoc params
                       :session-id new-session-id
                       :log-data [])]
    (swap! sessions #(assoc % new-session-id session))
    (response session)))

(defn upload-data-chunk [{{session-id :session} :params}]
  (let [session (@sessions session-id)
        finished (:finished session)]
    (if (or (not session) finished)
      (status 403 (response "Session finished or not found."))
      )))

(defn finish-session [{{session-id :session} :params}]
  (response {}))

(defroutes logging-routes
  (GET "/new-session" [] (wrap-json-response (wrap-json-body new-session)))
  (POST "/upload-data" [] (wrap-json-response (wrap-json-body upload-data-chunk)))
  (POST "/finish-session" [] (wrap-json-response (wrap-json-body finish-session))))
