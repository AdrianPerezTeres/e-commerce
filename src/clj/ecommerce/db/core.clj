(ns ecommerce.db.core
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [mount.core :refer [defstate]]
            [ecommerce.config :refer [config]])
  (:import [com.zaxxer.hikari HikariDataSource HikariConfig]))

(defn- make-pool [db-spec]
  (let [config (doto (HikariConfig.)
                 (.setJdbcUrl (str "jdbc:postgresql://"
                                   (:host db-spec) ":"
                                   (:port db-spec) "/"
                                   (:dbname db-spec)))
                 (.setUsername (:user db-spec))
                 (.setPassword (:password db-spec))
                 (.setMaximumPoolSize 10)
                 (.setMinimumIdle 2))]
    (HikariDataSource. config)))

(defstate datasource
  :start (make-pool (:database config))
  :stop  (.close datasource))

(def default-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn execute! [sql-params]
  (jdbc/execute! datasource sql-params default-opts))

(defn execute-one! [sql-params]
  (jdbc/execute-one! datasource sql-params default-opts))
