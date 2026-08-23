(ns ecommerce.server
  (:require [ring.adapter.jetty :as jetty]
            [mount.core :refer [defstate]]
            [ecommerce.config :refer [config]]
            [ecommerce.routes :refer [create-app]])
  (:import [org.eclipse.jetty.server Server]))

(defstate server
  :start (let [port (get-in config [:server :port])
               app  (create-app)]
           (println (str "Starting server on port " port))
           (jetty/run-jetty app {:port port :join? false}))
  :stop  (.stop ^Server server))
