(ns ecommerce.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [mount.core :refer [defstate]]))

(defstate config
  :start (-> "config.edn"
             io/resource
             (aero/read-config {:profile :default})))
