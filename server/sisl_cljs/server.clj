(ns sisl-cljs.server
  (:require
   [sisl-cljs.upload :refer [new-session upload-data finish-session]]
   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route]
   [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [clojure.java.io :as io]))

(defn wrap-handler [handler]
  (-> handler
      wrap-keyword-params
      wrap-json-params
      wrap-json-response))

(defroutes logging-routes
  (POST "/new-session.php" [] (wrap-handler new-session))
  (POST "/upload-data.php" [] (wrap-handler upload-data))
  (POST "/finish-session.php" [] (wrap-handler finish-session))
  (GET "/" [] (io/resource "public/index.html"))
  (route/resources "/")
  (route/not-found "Not Found."))
