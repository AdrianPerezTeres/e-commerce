(ns ecommerce.services.product-service-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.services.product-service :as svc]))

(deftest test-build-query
  (testing "no filters returns empty"
    (let [[where params] (#'svc/build-query {})]
      (is (= "" where))
      (is (empty? params))))

  (testing "search query adds ILIKE conditions"
    (let [[where params] (#'svc/build-query {:q "laptop"})]
      (is (clojure.string/includes? where "ILIKE"))
      (is (= 3 (count params)))
      (is (every? #(= "%laptop%" %) params))))

  (testing "category filter"
    (let [[where params] (#'svc/build-query {:category "Electronics"})]
      (is (clojure.string/includes? where "category = ?"))
      (is (= ["Electronics"] params))))

  (testing "both filters"
    (let [[where params] (#'svc/build-query {:q "phone" :category "Electronics"})]
      (is (clojure.string/includes? where "ILIKE"))
      (is (clojure.string/includes? where "category = ?"))
      (is (= 4 (count params))))))
