(ns ecommerce.config-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.config :refer [config]]
            [mount.core :as mount]))

(deftest test-config-loads
  (testing "config state is loaded with expected keys"
    (mount/start #'ecommerce.config/config)
    (try
      (is (map? config))
      (is (contains? config :server))
      (is (contains? config :database))
      (is (contains? config :auth))
      (is (number? (get-in config [:server :port])))
      (is (string? (get-in config [:database :host])))
      (is (string? (get-in config [:auth :jwt-secret])))
      (finally
        (mount/stop #'ecommerce.config/config)))))
