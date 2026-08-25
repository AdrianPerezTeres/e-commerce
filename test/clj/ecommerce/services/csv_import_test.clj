(ns ecommerce.services.csv-import-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.services.csv-import :as csv]
            [ecommerce.services.sanitize :as sanitize]
            [ecommerce.db.core :as db]
            [next.jdbc :as jdbc]
            [clojure.java.io :as io]))

(defn- create-temp-csv [content]
  (let [f (java.io.File/createTempFile "test-import" ".csv")]
    (spit f content)
    f))

(deftest test-parse-price
  (testing "valid prices"
    (is (= 29.99M (#'csv/parse-price "29.99")))
    (is (= 29.99M (#'csv/parse-price "$29.99")))
    (is (= 1000.00M (#'csv/parse-price "1,000.00")))
    (is (= 0M (#'csv/parse-price "0"))))
  (testing "invalid prices"
    (is (nil? (#'csv/parse-price "free")))
    (is (nil? (#'csv/parse-price "")))
    (is (nil? (#'csv/parse-price nil)))
    (is (nil? (#'csv/parse-price "abc"))))
  (testing "negative price returns nil"
    (is (nil? (#'csv/parse-price "-10")))))

(deftest test-parse-stock
  (testing "valid stock values"
    (is (= 100 (#'csv/parse-stock "100")))
    (is (= 0 (#'csv/parse-stock "0"))))
  (testing "invalid stock values"
    (is (nil? (#'csv/parse-stock "-5")))
    (is (nil? (#'csv/parse-stock "abc")))
    (is (nil? (#'csv/parse-stock "")))
    (is (nil? (#'csv/parse-stock nil)))))

(deftest test-parse-weight
  (testing "valid weights"
    (is (= 1.5M (#'csv/parse-weight "1.5")))
    (is (= 0.3M (#'csv/parse-weight "0.3"))))
  (testing "invalid weights"
    (is (nil? (#'csv/parse-weight "")))
    (is (nil? (#'csv/parse-weight nil)))
    (is (nil? (#'csv/parse-weight "heavy")))))

(deftest test-strip-html
  (testing "removes HTML tags"
    (is (= "alert('XSS')" (sanitize/strip-html "<script>alert('XSS')</script>")))
    (is (= "bold text" (sanitize/strip-html "<b>bold text</b>"))))
  (testing "passes through clean strings"
    (is (= "normal text" (sanitize/strip-html "normal text"))))
  (testing "handles nil"
    (is (nil? (sanitize/strip-html nil)))))

(deftest test-validate-row
  (testing "valid row passes validation"
    (let [result (#'csv/validate-row {:name "Product" :sku "SKU-1" :price 10M :stock 5} 1)]
      (is (:valid result))))
  (testing "missing name fails"
    (let [result (#'csv/validate-row {:name nil :sku "SKU-1" :price 10M :stock 5} 1)]
      (is (not (:valid result)))
      (is (some #(= "name is required" %) (:errors result)))))
  (testing "blank name fails"
    (let [result (#'csv/validate-row {:name "" :sku "SKU-1" :price 10M :stock 5} 1)]
      (is (not (:valid result)))))
  (testing "missing sku fails"
    (let [result (#'csv/validate-row {:name "Product" :sku nil :price 10M :stock 5} 1)]
      (is (not (:valid result)))))
  (testing "nil price fails"
    (let [result (#'csv/validate-row {:name "Product" :sku "SKU-1" :price nil :stock 5} 1)]
      (is (not (:valid result)))))
  (testing "nil stock fails"
    (let [result (#'csv/validate-row {:name "Product" :sku "SKU-1" :price 10M :stock nil} 1)]
      (is (not (:valid result))))))

(deftest test-parse-row
  (testing "parses a complete row"
    (let [result (#'csv/parse-row ["Widget" "WG-001" "A widget" "Electronics" "29.99" "100" "0.5"])]
      (is (= "Widget" (:name result)))
      (is (= "WG-001" (:sku result)))
      (is (= 29.99M (:price result)))
      (is (= 100 (:stock result)))
      (is (= 0.5M (:weight-kg result)))))
  (testing "handles price with dollar sign"
    (let [result (#'csv/parse-row ["Mouse" "M-001" "" "" "$29.99" "10" "0.1"])]
      (is (= 29.99M (:price result)))))
  (testing "handles non-numeric price"
    (let [result (#'csv/parse-row ["Mat" "YM-015" "" "" "free" "10" "1"])]
      (is (nil? (:price result)))))
  (testing "handles negative stock"
    (let [result (#'csv/parse-row ["Lamp" "DL-007" "" "" "25" "-5" "1"])]
      (is (nil? (:stock result)))))
  (testing "handles blank fields"
    (let [result (#'csv/parse-row ["Widget" "W-1" "" "" "10" "5" ""])]
      (is (nil? (:description result)))
      (is (nil? (:category result)))
      (is (nil? (:weight-kg result))))))

(deftest test-has-malicious-content
  (testing "detects XSS as malicious"
    (is (some? (#'csv/has-malicious-content?
                [{:type "XSS" :field "name" :detail "test"}]))))
  (testing "detects SQL injection as malicious"
    (is (some? (#'csv/has-malicious-content?
                [{:type "SQL Injection" :field "name" :detail "test"}]))))
  (testing "formula injection alone is not malicious"
    (is (nil? (#'csv/has-malicious-content?
               [{:type "Formula Injection" :field "name" :detail "test"}]))))
  (testing "empty threats are not malicious"
    (is (nil? (#'csv/has-malicious-content? [])))))

(deftest test-detect-threats
  (testing "detects XSS in fields"
    (let [threats (#'csv/detect-threats ["<script>alert(1)</script>" "SKU" "" "" "10" "5" "1"] 1)]
      (is (some #(= "XSS" (:type %)) threats))))
  (testing "detects SQL injection"
    (let [threats (#'csv/detect-threats ["name" "SKU" "'; DROP TABLE products--" "" "10" "5" "1"] 1)]
      (is (some #(= "SQL Injection" (:type %)) threats))))
  (testing "detects formula injection"
    (let [threats (#'csv/detect-threats ["=1+1" "SKU" "" "" "10" "5" "1"] 1)]
      (is (some #(= "Formula Injection" (:type %)) threats))))
  (testing "clean data returns no threats"
    (let [threats (#'csv/detect-threats ["Widget" "W-1" "A widget" "Cat" "10" "5" "0.5"] 1)]
      (is (empty? threats))))
  (testing "skips blank values"
    (let [threats (#'csv/detect-threats ["Widget" "W-1" "" "" "10" "5" ""] 1)]
      (is (empty? threats)))))

(deftest test-process-csv-valid
  (testing "imports valid CSV rows"
    (let [f (create-temp-csv "name,sku,desc,cat,price,stock,weight\nWidget,W-1,A widget,Cat,10.00,5,0.5\nGadget,G-1,A gadget,Cat,20.00,3,1.0")]
      (with-redefs [next.jdbc/transact (fn [_ f _] (f :mock-tx))
                    db/tx-execute-one! (constantly {:id (java.util.UUID/randomUUID)})]
        (let [result (csv/process-csv f)]
          (is (= 2 (:imported result)))
          (is (= 0 (:skipped result)))
          (is (empty? (:errors result))))))))

(deftest test-process-csv-skips-blank-rows
  (testing "skips blank rows"
    (let [f (create-temp-csv "name,sku,desc,cat,price,stock,weight\n,,,,,,\nWidget,W-1,Desc,Cat,10,5,0.5")]
      (with-redefs [next.jdbc/transact (fn [_ f _] (f :mock-tx))
                    db/tx-execute-one! (constantly {:id (java.util.UUID/randomUUID)})]
        (let [result (csv/process-csv f)]
          (is (= 1 (:imported result)))
          (is (= 1 (:skipped result))))))))

(deftest test-process-csv-rejects-xss
  (testing "rejects rows with XSS content"
    (let [f (create-temp-csv "name,sku,desc,cat,price,stock,weight\n<script>alert(1)</script>,X-1,desc,Cat,10,5,0.5")]
      (with-redefs [next.jdbc/transact (fn [_ f _] (f :mock-tx))
                    db/tx-execute-one! (constantly {:id (java.util.UUID/randomUUID)})]
        (let [result (csv/process-csv f)]
          (is (= 0 (:imported result)))
          (is (= 1 (:rejected result)))
          (is (some #(= "XSS" (:type %)) (:threats result))))))))

(deftest test-process-csv-rejects-sqli
  (testing "rejects rows with SQL injection"
    (let [f (create-temp-csv "name,sku,desc,cat,price,stock,weight\nWidget,X-1,'; DROP TABLE products--,Cat,10,5,0.5")]
      (with-redefs [next.jdbc/transact (fn [_ f _] (f :mock-tx))
                    db/tx-execute-one! (constantly {:id (java.util.UUID/randomUUID)})]
        (let [result (csv/process-csv f)]
          (is (= 0 (:imported result)))
          (is (= 1 (:rejected result))))))))

(deftest test-process-csv-validation-errors
  (testing "reports validation errors for invalid rows"
    (let [f (create-temp-csv "name,sku,desc,cat,price,stock,weight\n,,,Cat,free,-1,0.5")]
      (with-redefs [next.jdbc/transact (fn [_ f _] (f :mock-tx))
                    db/tx-execute-one! (constantly {:id (java.util.UUID/randomUUID)})]
        (let [result (csv/process-csv f)]
          (is (= 0 (:imported result)))
          (is (seq (:errors result))))))))

(deftest test-process-csv-duplicates
  (testing "tracks duplicate SKUs"
    (let [f (create-temp-csv "name,sku,desc,cat,price,stock,weight\nWidget,W-1,desc,Cat,10,5,0.5")]
      (with-redefs [next.jdbc/transact (fn [_ f _] (f :mock-tx))
                    db/tx-execute-one! (constantly nil)]
        (let [result (csv/process-csv f)]
          (is (= 0 (:imported result)))
          (is (= 1 (count (:duplicates result)))))))))

(deftest test-process-csv-insert-exception
  (testing "captures insert exceptions"
    (let [f (create-temp-csv "name,sku,desc,cat,price,stock,weight\nWidget,W-1,desc,Cat,10,5,0.5")]
      (with-redefs [next.jdbc/transact (fn [_ f _] (f :mock-tx))
                    db/tx-execute-one! (fn [_ _] (throw (Exception. "constraint violation")))]
        (let [result (csv/process-csv f)]
          (is (= 0 (:imported result)))
          (is (seq (:errors result))))))))

(deftest test-process-csv-formula-injection-allowed
  (testing "formula injection rows are sanitized but not rejected"
    (let [f (create-temp-csv "name,sku,desc,cat,price,stock,weight\n=1+1,F-1,desc,Cat,10,5,0.5")]
      (with-redefs [next.jdbc/transact (fn [_ f _] (f :mock-tx))
                    db/tx-execute-one! (constantly {:id (java.util.UUID/randomUUID)})]
        (let [result (csv/process-csv f)]
          (is (= 1 (:imported result)))
          (is (= 0 (:rejected result)))
          (is (some #(= "Formula Injection" (:type %)) (:threats result))))))))
