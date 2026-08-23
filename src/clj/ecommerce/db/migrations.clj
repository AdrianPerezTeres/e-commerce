(ns ecommerce.db.migrations
  (:require [migratus.core :as migratus]
            [mount.core :as mount]
            [ecommerce.config :refer [config]]
            [ecommerce.db.core :refer [datasource]]))

(defn migration-config []
  {:store             :database
   :migration-dir     "migrations/"
   :command-separator "--;--"
   :db                {:datasource datasource}})

(defn migrate []
  (migratus/migrate (migration-config)))

(defn rollback []
  (migratus/rollback (migration-config)))

(defn -main [& _args]
  (mount/start #'ecommerce.config/config
               #'ecommerce.db.core/datasource)
  (migrate)
  (mount/stop))
