(ns ecommerce.services.product-service
  (:require [ecommerce.db.core :as db]
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
        sql    (str "SELECT * FROM products WHERE 1=1" where
                    " ORDER BY created_at DESC LIMIT ? OFFSET ?")
        params (into params [per-page offset])]
    (db/execute! (into [sql] params))))

(defn get-product [id]
  (db/execute-one! ["SELECT * FROM products WHERE id = ?::uuid" id]))

(defn create-product [data]
  (try
    (let [clean (sanitize/sanitize-product data "API create-product")]
      (db/execute-one!
       ["INSERT INTO products (name, sku, description, category, price, stock, weight_kg, created_by, updated_by)
         VALUES (?, ?, ?, ?, ?::decimal, ?::integer, ?::decimal, ?, ?)
         RETURNING *"
        (:name clean)
        (:sku clean)
        (:description clean)
        (:category clean)
        (:price clean)
        (:stock clean)
        (:weight-kg clean)
        (:created-by clean)
        (:updated-by clean)]))
    (catch Exception e
      (log/error e "Failed to create product")
      {:error (.getMessage e)})))

(defn update-product [id data]
  (let [existing (get-product id)]
    (if-not existing
      {:error "Product not found"}
      (try
        (let [clean (sanitize/sanitize-product data "API update-product")]
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
            (:name clean)
            (:sku clean)
            (:description clean)
            (:category clean)
            (:price clean)
            (:stock clean)
            (:weight-kg clean)
            (:updated-by clean)
            id]))
        (catch Exception e
          (log/error e "Failed to update product")
          {:error (.getMessage e)})))))

(defn delete-product [id]
  (let [result (db/execute-one! ["DELETE FROM products WHERE id = ?::uuid RETURNING id" id])]
    (if result
      {:deleted true}
      {:error "Product not found"})))

(defn delete-all-products []
  (let [result (db/execute-one! ["SELECT count(*) AS total FROM products"])]
    (db/execute! ["DELETE FROM products"])
    {:deleted (or (:total result) 0)}))

(defn import-csv [file]
  (csv/process-csv file))
