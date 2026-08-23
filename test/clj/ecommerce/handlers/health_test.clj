(ns ecommerce.handlers.health-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.handlers.health :as h]))

(deftest test-health-check
  (testing "returns 200 with status ok"
    (let [response (h/check {})]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status])))
      (is (string? (get-in response [:body :time]))))))
