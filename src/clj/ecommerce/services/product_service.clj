(ns ecommerce.services.product-service
  (:require [ecommerce.db.core :as db]
            [next.jdbc :as jdbc]
            [ecommerce.services.csv-import :as csv]
            [ecommerce.services.sanitize :as sanitize]
            [clojure.tools.logging :as log])
  (:import [java.util UUID]))

(defn- build-query [{:keys [q category]}]
  (let [[where params] (reduce
                        (fn [[w p] [condition values]]
                          [(str w condition) (into p values)])
                        ["" []]
                        (cond-> []
                          q        (conj [" AND (name ILIKE ? OR description ILIKE ? OR sku ILIKE ?)"
                                          [(str "%" q "%") (str "%" q "%") (str "%" q "%")]])
                          category (conj [" AND category = ?" [category]])))]
    [where params]))

(defn list-products [{:keys [q category page per-page]
                      :or   {page "1" per-page "20"}}]
  (let [page     (Integer/parseInt (str page))
        per-page (Integer/parseInt (str per-page))
        offset   (* (dec page) per-page)
        [where params] (build-query {:q q :category category})
        count-sql (str "SELECT count(*) AS total FROM products WHERE 1=1" where)
        total     (or (:total (db/execute-one! (into [count-sql] params))) 0)
        sql       (str "SELECT * FROM products WHERE 1=1" where
                       " ORDER BY created_at DESC LIMIT ? OFFSET ?")
        items     (db/execute! (into [sql] (into params [per-page offset])))]
    {:items    items
     :total    total
     :page     page
     :per-page per-page}))

(defn get-product [id]
  (db/execute-one! ["SELECT * FROM products WHERE id = ?::uuid" id]))

(defn create-product [data]
  (try
    (let [{:keys [data threats]} (sanitize/sanitize-product data "API create-product")]
      (if (sanitize/has-malicious-content? threats)
        {:error   "Input rejected — malicious content detected"
         :threats (mapv #(select-keys % [:field :type :detail]) threats)}
        (db/execute-one!
         ["INSERT INTO products (name, sku, description, category, price, stock, weight_kg, created_by, updated_by)
           VALUES (?, ?, ?, ?, ?::decimal, ?::integer, ?::decimal, ?, ?)
           RETURNING *"
          (:name data)
          (:sku data)
          (:description data)
          (:category data)
          (:price data)
          (:stock data)
          (:weight-kg data)
          (:created-by data)
          (:updated-by data)])))
    (catch Exception e
      (log/error e "Failed to create product")
      (let [msg (.getMessage e)]
        (cond
          (and msg (re-find #"duplicate key|unique constraint" msg))
          {:error "Duplicate SKU — a product with this SKU already exists"}
          :else
          {:error   "Failed to create product"
           :detail  msg})))))

(defn update-product [id data]
  (let [existing (get-product id)]
    (if-not existing
      {:error "Product not found"}
      (try
        (let [{:keys [data threats]} (sanitize/sanitize-product data "API update-product")]
          (if (sanitize/has-malicious-content? threats)
            {:error   "Input rejected — malicious content detected"
             :threats (mapv #(select-keys % [:field :type :detail]) threats)}
            (db/execute-one!
             ["UPDATE products
               SET name = COALESCE(?, name),
                   sku = COALESCE(?, sku),
                   description = COALESCE(?, description),
                   category = COALESCE(?, category),
                   price = COALESCE(?::decimal, price),
                   stock = COALESCE(?::integer, stock),
                   weight_kg = COALESCE(?::decimal, weight_kg),
                   updated_by = ?,
                   updated_at = NOW()
               WHERE id = ?::uuid
               RETURNING *"
              (:name data)
              (:sku data)
              (:description data)
              (:category data)
              (:price data)
              (:stock data)
              (:weight-kg data)
              (:updated-by data)
              id])))
        (catch Exception e
          (log/error e "Failed to update product")
          (let [msg (.getMessage e)]
            (cond
              (and msg (re-find #"duplicate key|unique constraint" msg))
              {:error "Duplicate SKU — a product with this SKU already exists"}
              :else
              {:error  "Failed to update product"
               :detail msg})))))))

(defn delete-product [id]
  (let [result (db/execute-one! ["DELETE FROM products WHERE id = ?::uuid RETURNING id" id])]
    (if result
      {:deleted true}
      {:error "Product not found"})))

(defn delete-all-products []
  (db/with-transaction [tx]
    (let [result (jdbc/execute-one! tx ["SELECT count(*) AS total FROM products"] db/default-opts)]
      (jdbc/execute! tx ["DELETE FROM order_items"] db/default-opts)
      (jdbc/execute! tx ["DELETE FROM orders"] db/default-opts)
      (jdbc/execute! tx ["DELETE FROM products"] db/default-opts)
      {:deleted (or (:total result) 0)})))

(defn import-csv [file]
  (csv/process-csv file))
