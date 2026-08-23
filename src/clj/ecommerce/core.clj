(ns ecommerce.core
  (:require [mount.core :as mount]
            [ecommerce.config]
            [ecommerce.db.core]
            [ecommerce.db.migrations :as migrations]
            [ecommerce.server])
  (:gen-class))

(defn -main [& _args]
  (mount/start)
  (migrations/migrate)
  (println "E-Commerce application started.")
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(mount/stop))))
