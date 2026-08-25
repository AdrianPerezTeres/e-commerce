(ns ecommerce.services.sanitize-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.services.sanitize :as sanitize]))

(deftest test-strip-formula-prefix
  (testing "strips dangerous prefixes"
    (is (= "1+1" (sanitize/strip-formula-prefix "=1+1")))
    (is (= "cmd" (sanitize/strip-formula-prefix "+cmd")))
    (is (= "100" (sanitize/strip-formula-prefix "-100")))
    (is (= "SUM(A1)" (sanitize/strip-formula-prefix "@SUM(A1)")))
    (is (= "pipe" (sanitize/strip-formula-prefix "|pipe"))))
  (testing "leaves safe strings alone"
    (is (= "normal text" (sanitize/strip-formula-prefix "normal text")))
    (is (= "price is $10" (sanitize/strip-formula-prefix "price is $10"))))
  (testing "handles nil"
    (is (nil? (sanitize/strip-formula-prefix nil)))))

(deftest test-sanitize-text
  (testing "strips HTML and formula prefixes together"
    (is (= "alert('xss')" (sanitize/sanitize-text "<script>alert('xss')</script>")))
    (is (= "1+1" (sanitize/sanitize-text "=1+1")))
    (is (= "alert('x')" (sanitize/sanitize-text "=<b>alert('x')</b>"))))
  (testing "nil passthrough"
    (is (nil? (sanitize/sanitize-text nil)))))

(deftest test-sanitize-product
  (testing "sanitizes name and description"
    (let [data   {:name "<script>bad</script>" :description "=SUM(A1)" :sku "TEST-1" :price 10}
          result (sanitize/sanitize-product data "test")]
      (is (= "bad" (:name result)))
      (is (= "SUM(A1)" (:description result)))
      (is (= "TEST-1" (:sku result)))
      (is (= 10 (:price result)))))
  (testing "leaves clean data unchanged"
    (let [data   {:name "Widget" :description "A nice widget" :sku "W-001" :price 29.99}
          result (sanitize/sanitize-product data "test")]
      (is (= "Widget" (:name result)))
      (is (= "A nice widget" (:description result))))))

(deftest test-detect-threats
  (testing "detects XSS"
    (let [threats (sanitize/detect-threats {"name" "<script>x</script>"} "test")]
      (is (= 1 (count threats)))
      (is (= "XSS" (:type (first threats))))))
  (testing "detects SQL injection"
    (let [threats (sanitize/detect-threats {"name" "'; DROP TABLE products;--"} "test")]
      (is (some #(= "SQL Injection" (:type %)) threats))))
  (testing "detects formula injection"
    (let [threats (sanitize/detect-threats {"name" "=1+1"} "test")]
      (is (some #(= "Formula Injection" (:type %)) threats))))
  (testing "returns empty for clean data"
    (is (empty? (sanitize/detect-threats {"name" "Widget"} "test")))))
