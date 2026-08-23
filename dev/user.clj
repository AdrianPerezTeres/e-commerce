(ns user
  (:require [mount.core :as mount]
            [clojure.tools.namespace.repl :as tn]
            [ecommerce.config]
            [ecommerce.db.core]
            [ecommerce.db.migrations :as migrations]
            [ecommerce.server]))

(tn/set-refresh-dirs "src/clj")

(defn go
  "Start all mount states and run migrations."
  []
  (mount/start)
  (migrations/migrate)
  (println "System started. Browse to http://localhost:8080")
  :ready)

(defn stop
  "Stop all mount states."
  []
  (mount/stop)
  :stopped)

(defn reset
  "Stop, reload changed namespaces, restart."
  []
  (stop)
  (tn/refresh :after 'user/go))
