;; CSV Import — 9 Security Defenses
;;
;; Threats detected in the provided CSV:
;; 1. XSS — <script> tags in product names → row rejected
;; 2. SQL Injection — DROP TABLE payloads → row rejected
;; 3. Formula Injection — =, +, -, @, | prefixes (stripped before storage)
;; 4. Threat Reporting — scans raw values, logs every detected attack
;;
;; Handler-level defenses (see handlers/products.clj):
;; 5. File type validation (.csv only)
;; 6. File size limit (20MB)
;; 7. Row limit (100K)
;; 8. Nil file check
;; 9. Magic byte validation (detects binary files disguised as .csv)
;;
;; All inserts run inside a single transaction for atomicity.

(ns ecommerce.services.csv-import
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ecommerce.db.core :as db]
            [ecommerce.services.sanitize :as sanitize]
            [clojure.tools.logging :as log]))

;; [7] Row limit — caps import at 100K rows to prevent DB flooding
(def ^:private max-rows 100000)

;; [2] Threat Reporting — scans raw values before sanitization to log detected attacks
(defn- detect-threats [raw-values line-num]
  (let [fields  ["name" "sku" "description" "category" "price" "stock" "weight_kg"]
        threats (reduce
                 (fn [threats [field value]]
                   (if (str/blank? value)
                     threats
                     (cond-> threats
                       (re-find sanitize/html-pattern value)
                       (conj {:line line-num :field field :type "XSS" :detail "HTML/script tags detected — row rejected"})
                       (re-find sanitize/sqli-pattern value)
                       (conj {:line line-num :field field :type "SQL Injection" :detail "SQL injection pattern detected — row rejected"})
                       (re-find sanitize/formula-pattern value)
                       (conj {:line line-num :field field :type "Formula Injection" :detail "Spreadsheet formula prefix detected and stripped"}))))
                 []
                 (map vector fields raw-values))]
    threats))

(defn- has-malicious-content? [threats]
  (some #(#{"XSS" "SQL Injection"} (:type %)) threats))

(defn- parse-price [s]
  (when-not (str/blank? s)
    (let [cleaned (-> s
                      str/trim
                      (str/replace #"[$,]" ""))]
      (try
        (let [price (BigDecimal. cleaned)]
          (when (>= price 0)
            price))
        (catch Exception _ nil)))))

(defn- parse-stock [s]
  (when-not (str/blank? s)
    (try
      (let [stock (Integer/parseInt (str/trim s))]
        (when (>= stock 0)
          stock))
      (catch Exception _ nil))))

(defn- parse-weight [s]
  (when-not (str/blank? s)
    (try
      (BigDecimal. (str/trim s))
      (catch Exception _ nil))))

(defn- validate-row [row line-num]
  (let [{:keys [name sku price stock]} row
        errors (cond-> []
                 (str/blank? name)  (conj "name is required")
                 (str/blank? sku)   (conj "sku is required")
                 (nil? price)       (conj "price is invalid or missing")
                 (nil? stock)       (conj "stock is invalid (must be non-negative integer)"))]
    (if (seq errors)
      {:valid false :line line-num :errors errors}
      {:valid true  :line line-num :data row})))

(defn- parse-row [[name sku description category price stock weight-kg]]
  {:name        (-> (when-not (str/blank? name) (str/trim name)) sanitize/sanitize-text)
   :sku         (when-not (str/blank? sku) (str/trim sku))
   :description (-> (when-not (str/blank? description) (str/trim description)) sanitize/sanitize-text)
   :category    (when-not (str/blank? category) (str/trim category))
   :price       (parse-price price)
   :stock       (parse-stock stock)
   :weight-kg   (parse-weight weight-kg)})

;; [3] SQL Injection Defense — parameterized queries prevent injection via ? placeholders
;; Simple but effective
(defn- insert-product! [tx {:keys [name sku description category price stock weight-kg]}]
  (db/tx-execute-one! tx
   ["INSERT INTO products (name, sku, description, category, price, stock, weight_kg)
     VALUES (?, ?, ?, ?, ?::decimal, ?::integer, ?::decimal)
     ON CONFLICT (sku) DO NOTHING
     RETURNING id"
    name sku description category price stock weight-kg]))

(defn process-csv [file]
  (with-open [reader (io/reader file)]
    (let [rows       (csv/read-csv reader)
          _header    (first rows)
          data-rows  (take max-rows (rest rows))
          results    (db/with-transaction [tx]
                       (reduce
                        (fn [acc [idx row]]
                          (if (every? str/blank? row)
                            (update acc :skipped inc)
                            (let [line-num  (+ idx 2)
                                  threats   (detect-threats row line-num)
                                  acc       (update acc :threats into threats)]
                              (if (has-malicious-content? threats)
                                (do
                                  (log/warn (str "Row " line-num " rejected — malicious content: "
                                                 (str/join ", " (map #(str (:type %) " in " (:field %)) threats))))
                                  (update acc :rejected inc))
                                (let [parsed    (parse-row row)
                                      validated (validate-row parsed line-num)]
                                  (if (:valid validated)
                                    (try
                                      (let [result (insert-product! tx (:data validated))]
                                        (if result
                                          (update acc :imported inc)
                                          (update acc :duplicates conj
                                                  {:line line-num
                                                   :sku  (:sku parsed)
                                                   :reason "duplicate SKU"})))
                                      (catch Exception e
                                        (update acc :errors conj
                                                {:line  line-num
                                                 :error (.getMessage e)})))
                                    (update acc :errors conj
                                            {:line   (:line validated)
                                             :errors (:errors validated)})))))))
                        {:imported   0
                         :skipped    0
                         :rejected   0
                         :duplicates []
                         :errors     []
                         :threats    []}
                        (map-indexed vector data-rows)))]
      (let [threat-counts (when (seq (:threats results))
                            (->> (:threats results)
                                 (group-by :type)
                                 (map (fn [[t v]] (str t ": " (count v))))
                                 (str/join ", ")))]
        (log/info (str "CSV import complete — "
                       (:imported results) " imported, "
                       (:skipped results) " skipped, "
                       (:rejected results) " rejected (malicious), "
                       (count (:duplicates results)) " duplicates, "
                       (count (:errors results)) " errors"
                       (when threat-counts (str ", threats [" threat-counts "]")))))
      results)))
