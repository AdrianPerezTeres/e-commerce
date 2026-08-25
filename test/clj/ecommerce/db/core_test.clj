(ns ecommerce.db.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.db.core :as db]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(deftest test-default-opts
  (testing "default-opts uses unqualified kebab maps"
    (is (= rs/as-unqualified-kebab-maps (:builder-fn db/default-opts)))))

(deftest test-execute!
  (testing "delegates to jdbc/execute! with datasource and default-opts"
    (let [called-args (atom nil)]
      (with-redefs [jdbc/execute!  (fn [ds sql opts]
                                     (reset! called-args {:ds ds :sql sql :opts opts})
                                     [{:id 1}])
                    db/datasource :mock-ds]
        (let [result (db/execute! ["SELECT 1"])]
          (is (= [{:id 1}] result))
          (is (= :mock-ds (:ds @called-args)))
          (is (= ["SELECT 1"] (:sql @called-args)))
          (is (= db/default-opts (:opts @called-args))))))))

(deftest test-execute-one!
  (testing "delegates to jdbc/execute-one! with datasource and default-opts"
    (let [called-args (atom nil)]
      (with-redefs [jdbc/execute-one! (fn [ds sql opts]
                                        (reset! called-args {:ds ds :sql sql :opts opts})
                                        {:id 1})
                    db/datasource :mock-ds]
        (let [result (db/execute-one! ["SELECT 1"])]
          (is (= {:id 1} result))
          (is (= :mock-ds (:ds @called-args))))))))

(deftest test-tx-execute-one!
  (testing "delegates to jdbc/execute-one! with tx and default-opts"
    (let [called-args (atom nil)]
      (with-redefs [jdbc/execute-one! (fn [tx sql opts]
                                        (reset! called-args {:tx tx :sql sql :opts opts})
                                        {:id 1})]
        (let [result (db/tx-execute-one! :mock-tx ["INSERT INTO x"])]
          (is (= {:id 1} result))
          (is (= :mock-tx (:tx @called-args)))
          (is (= db/default-opts (:opts @called-args))))))))

(deftest test-with-transaction-macro
  (testing "with-transaction binds tx and executes body"
    (with-redefs [next.jdbc/transact (fn [_ f _] (f :mock-tx))]
      (let [result (db/with-transaction [tx]
                     (is (= :mock-tx tx))
                     :done)]
        (is (= :done result))))))
