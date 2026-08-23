(ns ecommerce.handlers.products-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.handlers.products :as h]))

(deftest test-get-product-not-found
  (testing "returns 404 when service returns nil"
    (with-redefs [ecommerce.services.product-service/get-product (constantly nil)]
      (let [response (h/get-product {:path-params {:id "non-existent"}})]
        (is (= 404 (:status response)))
        (is (= "Product not found" (get-in response [:body :error])))))))

(deftest test-get-product-found
  (testing "returns 200 with product"
    (let [product {:id "123" :name "Test" :sku "T-1"}]
      (with-redefs [ecommerce.services.product-service/get-product (constantly product)]
        (let [response (h/get-product {:path-params {:id "123"}})]
          (is (= 200 (:status response)))
          (is (= product (:body response))))))))

(deftest test-create-product-success
  (testing "returns 201 on success"
    (let [product {:id "123" :name "Test" :sku "T-1"}]
      (with-redefs [ecommerce.services.product-service/create-product (constantly product)]
        (let [response (h/create-product {:body-params {:name "Test" :sku "T-1"}})]
          (is (= 201 (:status response))))))))

(deftest test-create-product-error
  (testing "returns 400 on error"
    (with-redefs [ecommerce.services.product-service/create-product
                  (constantly {:error "duplicate sku"})]
      (let [response (h/create-product {:body-params {:name "Test" :sku "T-1"}})]
        (is (= 400 (:status response)))))))

(deftest test-update-product-not-found
  (testing "returns 404 when product not found"
    (with-redefs [ecommerce.services.product-service/update-product
                  (constantly {:error "Product not found"})]
      (let [response (h/update-product {:path-params {:id "123"} :body-params {}})]
        (is (= 404 (:status response)))))))

(deftest test-delete-product-success
  (testing "returns 204 on success"
    (with-redefs [ecommerce.services.product-service/delete-product
                  (constantly {:deleted true})]
      (let [response (h/delete-product {:path-params {:id "123"}})]
        (is (= 204 (:status response)))))))

(deftest test-delete-product-not-found
  (testing "returns 404 when not found"
    (with-redefs [ecommerce.services.product-service/delete-product
                  (constantly {:error "Product not found"})]
      (let [response (h/delete-product {:path-params {:id "123"}})]
        (is (= 404 (:status response)))))))
