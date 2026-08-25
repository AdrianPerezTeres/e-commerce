(ns ecommerce.services.product-service-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.services.product-service :as svc]
            [ecommerce.db.core :as db]))

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

(deftest test-list-products
  (testing "returns paginated results with total"
    (let [items [{:id "p1" :name "Widget"}]]
      (with-redefs [db/execute-one! (constantly {:total 1})
                    db/execute!     (constantly items)]
        (let [result (svc/list-products {})]
          (is (= items (:items result)))
          (is (= 1 (:total result)))
          (is (= 1 (:page result)))
          (is (= 20 (:per-page result)))))))

  (testing "respects page and per-page params"
    (with-redefs [db/execute-one! (constantly {:total 50})
                  db/execute!     (constantly [])]
      (let [result (svc/list-products {:page "2" :per-page "10"})]
        (is (= 2 (:page result)))
        (is (= 10 (:per-page result))))))

  (testing "handles numeric page params"
    (with-redefs [db/execute-one! (constantly {:total 0})
                  db/execute!     (constantly [])]
      (let [result (svc/list-products {:page 3 :per-page 5})]
        (is (= 3 (:page result)))
        (is (= 5 (:per-page result))))))

  (testing "passes search and category filters"
    (let [executed-sql (atom nil)]
      (with-redefs [db/execute-one! (constantly {:total 0})
                    db/execute!     (fn [params] (reset! executed-sql (first params)) [])]
        (svc/list-products {:q "laptop" :category "Electronics"})
        (is (clojure.string/includes? @executed-sql "ILIKE"))
        (is (clojure.string/includes? @executed-sql "category = ?")))))

  (testing "handles nil count result"
    (with-redefs [db/execute-one! (constantly nil)
                  db/execute!     (constantly [])]
      (let [result (svc/list-products {})]
        (is (= 0 (:total result)))))))

(deftest test-get-product
  (testing "returns product when found"
    (let [product {:id "p1" :name "Widget"}]
      (with-redefs [db/execute-one! (constantly product)]
        (is (= product (svc/get-product "p1"))))))
  (testing "returns nil when not found"
    (with-redefs [db/execute-one! (constantly nil)]
      (is (nil? (svc/get-product "nonexistent"))))))

(deftest test-create-product
  (testing "creates and returns product"
    (let [product {:id "p1" :name "Widget" :sku "W-1"}]
      (with-redefs [db/execute-one! (constantly product)]
        (let [result (svc/create-product {:name "Widget" :sku "W-1" :price "10" :stock "5"})]
          (is (= "p1" (:id result)))))))
  (testing "returns error on exception"
    (with-redefs [db/execute-one! (fn [_] (throw (Exception. "duplicate key")))]
      (let [result (svc/create-product {:name "Widget" :sku "W-1"})]
        (is (= "Failed to create product" (:error result)))))))

(deftest test-update-product
  (testing "updates existing product"
    (let [existing {:id "p1" :name "Old"}
          updated  {:id "p1" :name "New"}
          call-count (atom 0)]
      (with-redefs [db/execute-one! (fn [_]
                                      (swap! call-count inc)
                                      (if (= 1 @call-count) existing updated))]
        (let [result (svc/update-product "p1" {:name "New"})]
          (is (= "New" (:name result)))))))
  (testing "returns error when product not found"
    (with-redefs [db/execute-one! (constantly nil)]
      (let [result (svc/update-product "nonexistent" {:name "New"})]
        (is (= "Product not found" (:error result))))))
  (testing "returns error on exception"
    (let [call-count (atom 0)]
      (with-redefs [db/execute-one! (fn [_]
                                      (swap! call-count inc)
                                      (if (= 1 @call-count)
                                        {:id "p1" :name "Widget"}
                                        (throw (Exception. "db error"))))]
        (let [result (svc/update-product "p1" {:name "New"})]
          (is (= "Failed to update product" (:error result))))))))

(deftest test-delete-product
  (testing "deletes existing product"
    (with-redefs [db/execute-one! (constantly {:id "p1"})]
      (is (= {:deleted true} (svc/delete-product "p1")))))
  (testing "returns error when not found"
    (with-redefs [db/execute-one! (constantly nil)]
      (is (= {:error "Product not found"} (svc/delete-product "nonexistent"))))))

(deftest test-delete-all-products
  (testing "deletes all products and returns count"
    (with-redefs [db/execute-one! (constantly {:total 5})
                  db/execute!     (constantly nil)]
      (let [result (svc/delete-all-products)]
        (is (= 5 (:deleted result))))))
  (testing "handles nil total"
    (with-redefs [db/execute-one! (constantly {:total nil})
                  db/execute!     (constantly nil)]
      (let [result (svc/delete-all-products)]
        (is (= 0 (:deleted result)))))))

(deftest test-import-csv
  (testing "delegates to csv/process-csv"
    (let [file (java.io.File/createTempFile "test" ".csv")]
      (with-redefs [ecommerce.services.csv-import/process-csv (constantly {:imported 5})]
        (is (= {:imported 5} (svc/import-csv file)))))))
