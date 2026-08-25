(ns ecommerce.server-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.server :as server]
            [ecommerce.config :refer [config]]
            [mount.core :as mount]))

(deftest test-server-starts-and-stops
  (testing "server starts on configured port and can be stopped"
    (mount/start #'ecommerce.config/config)
    (try
      (with-redefs [config (assoc-in config [:server :port] 0)]
        (mount/start #'ecommerce.server/server)
        (try
          (is (some? server/server))
          (is (.isStarted server/server))
          (finally
            (mount/stop #'ecommerce.server/server))))
      (finally
        (mount/stop #'ecommerce.config/config)))))
