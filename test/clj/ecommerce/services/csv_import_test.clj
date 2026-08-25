(ns ecommerce.services.csv-import-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.services.csv-import :as csv]
            [ecommerce.services.sanitize :as sanitize]
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
    (is (nil? (#'csv/parse-price "abc")))))

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
  (testing "handles XSS in name"
    (let [result (#'csv/parse-row ["<script>alert('x')</script>" "XS-001" "" "" "10" "1" ""])]
      (is (= "alert('x')" (:name result)))))
  (testing "handles price with dollar sign"
    (let [result (#'csv/parse-row ["Mouse" "M-001" "" "" "$29.99" "10" "0.1"])]
      (is (= 29.99M (:price result)))))
  (testing "handles non-numeric price"
    (let [result (#'csv/parse-row ["Mat" "YM-015" "" "" "free" "10" "1"])]
      (is (nil? (:price result)))))
  (testing "handles negative stock"
    (let [result (#'csv/parse-row ["Lamp" "DL-007" "" "" "25" "-5" "1"])]
      (is (nil? (:stock result))))))
