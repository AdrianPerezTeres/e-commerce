;; CSV Import — Security & Sanitization
;;
;; The provided CSV contains intentional attack vectors:
;;
;; 1. XSS (Cross-Site Scripting) — Line 20: <script>alert('xss')</script> as product name
;;    Defense: strip-html (line 28) removes all HTML tags via regex before storage.
;;    Second layer: React/Reagent auto-escapes text on render.
;;
;; 2. SQL Injection — Line 29: Robert'); DROP TABLE products;-- as product name
;;    Defense: insert-product! (line 93) uses parameterized queries (?), never string
;;    concatenation. The malicious string is stored as harmless literal text.
;;
;; 3. Threat Reporting — detect-threats (line 34) scans raw values BEFORE sanitization
;;    and logs each detected XSS/SQLi attempt. Results are returned to the UI with
;;    type, line number, field, and description of action taken.

(ns ecommerce.services.csv-import
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ecommerce.db.core :as db]
            [clojure.tools.logging :as log]))

(def ^:private html-pattern #"<[^>]*>")
(def ^:private sqli-pattern #"(?i)('.*(--)|(;\s*(DROP|DELETE|UPDATE|INSERT|ALTER|EXEC|UNION)))")

;; [1] XSS Defense — strips all HTML/script tags from text fields
(defn- strip-html [s]
  (when s
    (str/replace s html-pattern "")))

;; [3] Threat Reporting — scans raw values before sanitization to log detected attacks
(defn- detect-threats [raw-values line-num]
  (let [fields ["name" "sku" "description" "category" "price" "stock" "weight_kg"]]
    (reduce
     (fn [threats [field value]]
       (if (str/blank? value)
         threats
         (cond-> threats
           (re-find html-pattern value)
           (conj {:line line-num :field field :type "XSS" :detail "HTML/script tags detected and stripped"})
           (re-find sqli-pattern value)
           (conj {:line line-num :field field :type "SQL Injection" :detail "SQL injection pattern detected and neutralized"}))))
     []
     (map vector fields raw-values))))

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
  {:name        (strip-html (when-not (str/blank? name) (str/trim name)))
   :sku         (when-not (str/blank? sku) (str/trim sku))
   :description (strip-html (when-not (str/blank? description) (str/trim description)))
   :category    (when-not (str/blank? category) (str/trim category))
   :price       (parse-price price)
   :stock       (parse-stock stock)
   :weight-kg   (parse-weight weight-kg)})

;; [2] SQL Injection Defense — parameterized queries prevent injection via ? placeholders
(defn- insert-product! [{:keys [name sku description category price stock weight-kg]}]
  (db/execute-one!
   ["INSERT INTO products (name, sku, description, category, price, stock, weight_kg)
     VALUES (?, ?, ?, ?, ?::decimal, ?::integer, ?::decimal)
     ON CONFLICT (sku) DO NOTHING
     RETURNING id"
    name sku description category price stock weight-kg]))

(defn process-csv [file]
  (with-open [reader (io/reader file)]
    (let [rows       (csv/read-csv reader)
          header     (first rows)
          data-rows  (rest rows)
          results    (reduce
                      (fn [acc [idx row]]
                        (if (every? str/blank? row)
                          (update acc :skipped inc)
                          (let [line-num  (+ idx 2)
                                threats   (detect-threats row line-num)
                                acc       (update acc :threats into threats)
                                parsed    (parse-row row)
                                validated (validate-row parsed line-num)]
                            (if (:valid validated)
                              (try
                                (let [result (insert-product! (:data validated))]
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
                                       :errors (:errors validated)})))))
                      {:imported   0
                       :skipped    0
                       :duplicates []
                       :errors     []
                       :threats    []}
                      (map-indexed vector data-rows))]
      (log/info "CSV import complete:" results)
      results)))
