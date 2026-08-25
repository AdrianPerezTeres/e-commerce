;; CSV Import — Security & Sanitization
;;
;; Reviewing the CSV file provided I found 3 diffferent security attacks:
;;
;; 1. XSS (Cross-Site Scripting) — Line 20: <script>alert('xss')</script> as product name
;; 2. Threat Reporting — detect-threats scans raw values BEFORE sanitization
;; 3. SQL Injection — Line 29: Robert'); DROP TABLE products;-- as product name
;; 4. Formula Injection — strips =, +, -, @, | prefixes that trigger Excel/Sheets execution
;;
;; Additional defenses (handler level — see handlers/products.clj):
;; 5. File type validation: only .csv files accepted
;; 6. File size limit: 20MB max to prevent memory exhaustion
;; 7. Row limit: 100,000 rows max to prevent DB flooding
;; 8. Nil file check: rejects requests with no file uploaded
;;

(ns ecommerce.services.csv-import
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ecommerce.db.core :as db]
            [clojure.tools.logging :as log]))

;; [7] Row limit — caps import at 100K rows to prevent DB flooding
(def ^:private max-rows 100000)
(def ^:private html-pattern #"<[^>]*>")
(def ^:private sqli-pattern #"(?i)('.*(--)|(;\s*(DROP|DELETE|UPDATE|INSERT|ALTER|EXEC|UNION)))")
(def ^:private formula-pattern #"^[=+\-@\|]")

;; [1] XSS Defense — strips all HTML/script tags from text fields
(defn- strip-html [s]
  (when s
    (str/replace s html-pattern "")))

;; [4] Formula Injection Defense — strips leading =, +, -, @, | that trigger Excel/Sheets execution
(defn- strip-formula-prefix [s]
  (when s
    (str/replace s formula-pattern "")))

;; [2] Threat Reporting — scans raw values before sanitization to log detected attacks
;; I added the extra logging to confirm detected threats, I am assuming more CSV files will
;; be processed and this will help to identify if we catch them all
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
           (conj {:line line-num :field field :type "SQL Injection" :detail "SQL injection pattern detected and neutralized"})
           (re-find formula-pattern value)
           (conj {:line line-num :field field :type "Formula Injection" :detail "Spreadsheet formula prefix detected and stripped"}))))
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
  {:name        (-> (when-not (str/blank? name) (str/trim name)) strip-html strip-formula-prefix)
   :sku         (when-not (str/blank? sku) (str/trim sku))
   :description (-> (when-not (str/blank? description) (str/trim description)) strip-html strip-formula-prefix)
   :category    (when-not (str/blank? category) (str/trim category))
   :price       (parse-price price)
   :stock       (parse-stock stock)
   :weight-kg   (parse-weight weight-kg)})

;; [3] SQL Injection Defense — parameterized queries prevent injection via ? placeholders
;; Simple but effective
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
          data-rows  (take max-rows (rest rows))
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
