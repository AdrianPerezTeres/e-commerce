(ns ecommerce.handlers.orders
  (:require [ecommerce.services.order-service :as service]))

(defn create-order [request]
  (let [order-data (:body-params request)
        result (service/create-order order-data)]
    (if (:error result)
      {:status 400 :body result}
      {:status 201 :body result})))

(defn list-orders [_request]
  {:status 200
   :body   (service/list-orders)})

(defn get-order [request]
  (let [id (get-in request [:path-params :id])
        order (service/get-order id)]
    (if order
      {:status 200 :body order}
      {:status 404 :body {:error "Order not found"}})))
