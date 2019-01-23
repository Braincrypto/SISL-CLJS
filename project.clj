(defproject sisl-cljs "0.1.0-SNAPSHOT"
  :description "A ClojureScript implementation of the SISL Hero research tool."
  :url "http://cortical.csl.sri.com/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.439"]
                 [org.clojure/core.async "0.4.474"]
                 [reagent "0.8.1"]
                 [cheshire "5.8.1"]
                 [cljs-ajax "0.7.4"]
                 [cljs-bach "0.3.0"]
                 [markdown-clj "1.0.2"]
                 [ring/ring-json "0.4.0"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.16"]
            [lein-ring "0.12.2"]]

  :source-paths ["server"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild
  {:builds
   [{:id "dev"
     :source-paths ["src" "server"]
     :figwheel { :on-jsload "sisl-cljs.core/on-js-reload" }
     :compiler {:main sisl-cljs.core
                :output-to "resources/public/js/compiled/sisl-cljs.js"
                :output-dir "resources/public/js/compiled/out"
                :asset-path "js/compiled/out"
                :source-map-timestamp true }}
    {:id "min"
     :source-paths ["src"]
     :compiler {:main sisl-cljs.core
                :output-to "resources/public/js/compiled/sisl-cljs.js"
                :optimizations :advanced
                :pretty-print false}}]}

  :figwheel {
             ;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             }
  :ring {:handler sisl-cljs.server/logging-routes
         :port 3700})
