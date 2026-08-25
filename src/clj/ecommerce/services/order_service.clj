(ns ecommerce.services.order-service
  (:require [ecommerce.db.core :as db]
            [next.jdbc :as jdbc]
            [clojure.tools.logging :as log]))

(defn- validate-order-items [items]
  (when (or (nil? items) (empty? items))
    (throw (ex-info "Order must contain at least one item" {:error "empty order"})))
  (doseq [{:keys [product-id quantity]} items]
    (when-not product-id
      (throw (ex-info "product-id is required" {:error "missing product-id"})))
    (when (or (nil? quantity) (<= quantity 0))
      (throw (ex-info "quantity must be positive" {:error "invalid quantity"})))))

(defn- check-stock-and-get-product [tx product-id quantity]
  (let [product (jdbc/execute-one! tx
                  ["SELECT * FROM products WHERE id = ?::uuid FOR UPDATE" product-id]
                  db/default-opts)]
    (when-not product
      (throw (ex-info (str "Product not found: " product-id) {:product-id product-id})))
    (when (< (:stock product) quantity)
      (throw (ex-info (str "Insufficient stock for " (:name product)
                           ". Available: " (:stock product)
                           ", Requested: " quantity)
                      {:product-id product-id
                       :available  (:stock product)
                       :requested  quantity})))
    product))

(defn create-order [{:keys [items user-id]}]
  (try
    (validate-order-items items)
    (jdbc/with-transaction [tx db/datasource]
      (let [products-with-qty (mapv (fn [{:keys [product-id quantity]}]
                                      (let [product (check-stock-and-get-product tx product-id quantity)]
                                        (assoc product :order-quantity quantity)))
                                    items)
            total             (reduce (fn [sum p]
                                        (+ sum (* (:price p) (:order-quantity p))))
                                      0M products-with-qty)
            order             (jdbc/execute-one! tx
                                ["INSERT INTO orders (user_id, status, total)
                                  VALUES (?, 'paid', ?::decimal)
                                  RETURNING *"
                                 user-id total]
                                db/default-opts)]
        (doseq [p products-with-qty]
          (jdbc/execute-one! tx
            ["INSERT INTO order_items (order_id, product_id, quantity, unit_price)
              VALUES (?::uuid, ?::uuid, ?::integer, ?::decimal)"
             (:id order) (:id p) (:order-quantity p) (:price p)]
            db/default-opts)
          (jdbc/execute-one! tx
            ["UPDATE products SET stock = stock - ?::integer WHERE id = ?::uuid"
             (:order-quantity p) (:id p)]
            db/default-opts))
        (assoc order :items (mapv (fn [p]
                                    {:product-id (:id p)
                                     :name       (:name p)
                                     :quantity   (:order-quantity p)
                                     :unit-price (:price p)})
                                  products-with-qty))))
    (catch clojure.lang.ExceptionInfo e
      (log/warn "Order validation failed:" (.getMessage e))
      {:error (.getMessage e)})
    (catch Exception e
      (log/error e "Failed to create order")
      {:error (.getMessage e)})))

(defn list-orders []
  (db/execute! ["SELECT * FROM orders ORDER BY created_at DESC"]))

(defn get-order [id]
  (let [order (db/execute-one! ["SELECT * FROM orders WHERE id = ?::uuid" id])
        items (when order
                (db/execute! ["SELECT oi.*, p.name as product_name
                               FROM order_items oi
                               JOIN products p ON p.id = oi.product_id
                               WHERE oi.order_id = ?::uuid" id]))]
    (when order
      (assoc order :items items))))
