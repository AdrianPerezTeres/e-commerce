(ns ecommerce.services.csv-import
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ecommerce.db.core :as db]
            [clojure.tools.logging :as log]))

(defn- strip-html [s]
  (when s
    (str/replace s #"<[^>]*>" "")))

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
                          (let [parsed    (parse-row row)
                                validated (validate-row parsed (+ idx 2))]
                            (if (:valid validated)
                              (try
                                (let [result (insert-product! (:data validated))]
                                  (if result
                                    (update acc :imported inc)
                                    (update acc :duplicates conj
                                            {:line (+ idx 2)
                                             :sku  (:sku parsed)
                                             :reason "duplicate SKU"})))
                                (catch Exception e
                                  (update acc :errors conj
                                          {:line  (+ idx 2)
                                           :error (.getMessage e)})))
                              (update acc :errors conj
                                      {:line   (:line validated)
                                       :errors (:errors validated)})))))
                      {:imported   0
                       :skipped    0
                       :duplicates []
                       :errors     []}
                      (map-indexed vector data-rows))]
      (log/info "CSV import complete:" results)
      results)))
