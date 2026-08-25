(ns ecommerce.middleware.error-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.middleware.error :refer [wrap-exception]]))

(deftest test-wrap-exception-passthrough
  (testing "passes through successful responses"
    (let [handler (wrap-exception (fn [_] {:status 200 :body "ok"}))
          response (handler {:uri "/test" :request-method :get})]
      (is (= 200 (:status response)))
      (is (= "ok" (:body response))))))

(deftest test-wrap-exception-catches-error
  (testing "catches exception and returns 500"
    (let [handler (wrap-exception (fn [_] (throw (Exception. "boom"))))
          response (handler {:uri "/test" :request-method :get})]
      (is (= 500 (:status response)))
      (is (= "Internal server error" (get-in response [:body :error])))
      (is (= "boom" (get-in response [:body :detail]))))))

(deftest test-wrap-exception-nil-message
  (testing "handles exception with nil message"
    (let [handler (wrap-exception (fn [_] (throw (Exception.))))
          response (handler {:uri "/test" :request-method :get})]
      (is (= 500 (:status response)))
      (is (nil? (get-in response [:body :detail]))))))
