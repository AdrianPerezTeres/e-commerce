(ns ecommerce.handlers.products
  (:require [ecommerce.services.product-service :as service]))

(defn list-products [request]
  (let [params (:params request)
        result (service/list-products params)]
    {:status 200
     :body   result}))

(defn get-product [request]
  (let [id (get-in request [:path-params :id])
        product (service/get-product id)]
    (if product
      {:status 200 :body product}
      {:status 404 :body {:error "Product not found"}})))

(defn create-product [request]
  (let [product-data (:body-params request)
        result (service/create-product product-data)]
    (if (:error result)
      {:status 400 :body result}
      {:status 201 :body result})))

(defn update-product [request]
  (let [id (get-in request [:path-params :id])
        product-data (:body-params request)
        result (service/update-product id product-data)]
    (if (:error result)
      {:status (if (= (:error result) "Product not found") 404 400)
       :body result}
      {:status 200 :body result})))

(defn delete-product [request]
  (let [id (get-in request [:path-params :id])
        result (service/delete-product id)]
    (if (:error result)
      {:status 404 :body result}
      {:status 204 :body nil})))

(defn import-csv [request]
  (let [file (get-in request [:multipart-params "file"])
        result (service/import-csv (:tempfile file))]
    {:status 200
     :body   result}))
