(ns sisl-cljs.server
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route]
   [ring.util.response :refer [response status]]
   [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [clojure.java.io :as io]))

(defonce current-session-id (atom 10000))

(defn new-session [{:keys [params] :as request}]
  (let [new-session-id (swap! current-session-id inc)]
    (println (str "Started new session with id " new-session-id))
    (response {:success true
               :session-id new-session-id})))

(defn upload-data-chunk [{{:keys [session data] :as params} :params :as request}]
  (println (str "Data uploaded: " (count data) " items for session " session "."))
  (response {:success true}))

(defn finish-session [{{:keys [session] :as params} :params :as request}]
  (println (str "Session " session " finished."))
  (response {:success true
             :code (str session "-FINISH-CODE")}))


(defn wrap-handler [handler]
  (-> handler
      wrap-keyword-params
      wrap-json-params
      wrap-json-response))

(defroutes logging-routes
  (POST "/new-session.php" [] (wrap-handler new-session))
  (POST "/upload-data.php" [] (wrap-handler upload-data-chunk))
  (POST "/finish-session.php" [] (wrap-handler finish-session))
  (GET "/" [] (io/resource "public/index.html"))
  (route/resources "/"))
