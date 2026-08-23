(ns ecommerce.handlers.orders-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.handlers.orders :as h]))

(deftest test-create-order-success
  (testing "returns 201 on success"
    (let [order {:id "123" :status "paid" :total 99.99}]
      (with-redefs [ecommerce.services.order-service/create-order (constantly order)]
        (let [response (h/create-order {:body-params {:items [{:product-id "p1" :quantity 1}]}})]
          (is (= 201 (:status response))))))))

(deftest test-create-order-error
  (testing "returns 400 on error"
    (with-redefs [ecommerce.services.order-service/create-order
                  (constantly {:error "Insufficient stock"})]
      (let [response (h/create-order {:body-params {:items [{:product-id "p1" :quantity 999}]}})]
        (is (= 400 (:status response)))))))

(deftest test-get-order-not-found
  (testing "returns 404 when order not found"
    (with-redefs [ecommerce.services.order-service/get-order (constantly nil)]
      (let [response (h/get-order {:path-params {:id "non-existent"}})]
        (is (= 404 (:status response)))))))

(deftest test-get-order-found
  (testing "returns 200 with order"
    (let [order {:id "123" :status "paid" :total 50.00 :items []}]
      (with-redefs [ecommerce.services.order-service/get-order (constantly order)]
        (let [response (h/get-order {:path-params {:id "123"}})]
          (is (= 200 (:status response)))
          (is (= order (:body response))))))))

(deftest test-list-orders
  (testing "returns 200 with orders list"
    (let [orders [{:id "1"} {:id "2"}]]
      (with-redefs [ecommerce.services.order-service/list-orders (constantly orders)]
        (let [response (h/list-orders {})]
          (is (= 200 (:status response)))
          (is (= 2 (count (:body response)))))))))
