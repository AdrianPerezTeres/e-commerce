(ns ecommerce.services.order-service-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.services.order-service :as svc]
            [ecommerce.db.core :as db]
            [next.jdbc :as jdbc]))

(deftest test-validate-order-items
  (testing "nil items throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"at least one item"
          (#'svc/validate-order-items nil))))
  (testing "empty items throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"at least one item"
          (#'svc/validate-order-items []))))
  (testing "missing product-id throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"product-id is required"
          (#'svc/validate-order-items [{:quantity 1}]))))
  (testing "nil quantity throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"quantity must be positive"
          (#'svc/validate-order-items [{:product-id "p1" :quantity nil}]))))
  (testing "zero quantity throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"quantity must be positive"
          (#'svc/validate-order-items [{:product-id "p1" :quantity 0}]))))
  (testing "negative quantity throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"quantity must be positive"
          (#'svc/validate-order-items [{:product-id "p1" :quantity -1}]))))
  (testing "valid items pass"
    (is (nil? (#'svc/validate-order-items [{:product-id "p1" :quantity 2}])))))

(deftest test-check-stock-and-get-product
  (testing "product not found throws"
    (with-redefs [jdbc/execute-one! (fn [_ _ _] nil)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Product not found"
            (#'svc/check-stock-and-get-product :mock-tx "p1" 1)))))
  (testing "insufficient stock throws"
    (with-redefs [jdbc/execute-one! (fn [_ _ _] {:id "p1" :name "Widget" :stock 2})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Insufficient stock"
            (#'svc/check-stock-and-get-product :mock-tx "p1" 5)))))
  (testing "sufficient stock returns product"
    (let [product {:id "p1" :name "Widget" :stock 10 :price 9.99M}]
      (with-redefs [jdbc/execute-one! (fn [_ _ _] product)]
        (is (= product (#'svc/check-stock-and-get-product :mock-tx "p1" 5)))))))

(deftest test-create-order-validation-failures
  (testing "empty items returns error"
    (let [result (svc/create-order {:items [] :user-id "u1"})]
      (is (:error result))
      (is (re-find #"at least one item" (:error result)))))
  (testing "nil items returns error"
    (let [result (svc/create-order {:items nil :user-id "u1"})]
      (is (:error result))))
  (testing "missing product-id returns error"
    (let [result (svc/create-order {:items [{:quantity 1}] :user-id "u1"})]
      (is (:error result))
      (is (re-find #"product-id" (:error result)))))
  (testing "invalid quantity returns error"
    (let [result (svc/create-order {:items [{:product-id "p1" :quantity 0}] :user-id "u1"})]
      (is (:error result)))))

(deftest test-create-order-success
  (testing "creates order with valid items"
    (let [product {:id "p1" :name "Widget" :stock 10 :price 9.99M}
          order   {:id "o1" :order-number "ECOMM-0001" :status "paid" :total 19.98M}
          tx-calls (atom [])]
      (with-redefs [jdbc/transact (fn [_ f _] (f :mock-tx))
                    jdbc/execute-one! (fn [_ sql-params _]
                                        (swap! tx-calls conj (first sql-params))
                                        (cond
                                          (re-find #"SELECT.*FROM products" (first sql-params))
                                          product
                                          (re-find #"INSERT INTO orders" (first sql-params))
                                          order
                                          :else nil))]
        (let [result (svc/create-order {:items [{:product-id "p1" :quantity 2}]
                                        :user-id "u1"})]
          (is (= "o1" (:id result)))
          (is (= 1 (count (:items result))))
          (is (= "Widget" (-> result :items first :name))))))))

(deftest test-create-order-stock-failure
  (testing "insufficient stock returns error"
    (with-redefs [jdbc/transact      (fn [_ f _] (f :mock-tx))
                  jdbc/execute-one!  (fn [_ _ _] {:id "p1" :name "Widget" :stock 1 :price 10M})]
      (let [result (svc/create-order {:items [{:product-id "p1" :quantity 5}]
                                      :user-id "u1"})]
        (is (:error result))
        (is (re-find #"Insufficient stock" (:error result)))))))

(deftest test-create-order-db-exception
  (testing "generic exception returns error"
    (with-redefs [jdbc/transact      (fn [_ f _] (f :mock-tx))
                  jdbc/execute-one!  (fn [_ _ _] (throw (Exception. "DB down")))]
      (let [result (svc/create-order {:items [{:product-id "p1" :quantity 1}]
                                      :user-id "u1"})]
        (is (:error result))
        (is (= "DB down" (:error result)))))))

(deftest test-list-orders
  (testing "returns orders list"
    (let [orders [{:id "o1"} {:id "o2"}]]
      (with-redefs [db/execute! (constantly orders)]
        (is (= orders (svc/list-orders)))))))

(deftest test-get-order-found
  (testing "returns order with items"
    (let [order {:id "o1" :status "paid"}
          items [{:product-id "p1" :quantity 2}]]
      (with-redefs [db/execute-one! (constantly order)
                    db/execute!     (constantly items)]
        (let [result (svc/get-order "o1")]
          (is (= "o1" (:id result)))
          (is (= items (:items result))))))))

(deftest test-get-order-not-found
  (testing "returns nil when order not found"
    (with-redefs [db/execute-one! (constantly nil)]
      (is (nil? (svc/get-order "nonexistent"))))))
