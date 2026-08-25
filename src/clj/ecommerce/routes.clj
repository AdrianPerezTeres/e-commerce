(ns ecommerce.routes
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ecommerce.middleware.auth :refer [wrap-auth wrap-require-auth wrap-require-role]]
            [ecommerce.middleware.error :refer [wrap-exception]]
            [ecommerce.handlers.products :as products]
            [ecommerce.handlers.orders :as orders]
            [ecommerce.handlers.health :as health]
            [ecommerce.handlers.auth :as auth]))

(def api-routes
  ["/api"
   ["/health" {:get health/check}]

   ["/auth"
    ["/login"  {:post auth/login}]
    ["/me"     {:get {:handler    auth/me
                      :middleware [wrap-require-auth]}}]]

   ["/products"
    ["" {:get    products/list-products
         :post   {:handler    products/create-product
                  :middleware [wrap-require-auth
                              #(wrap-require-role % "admin")]}}]
    ["/import" {:post {:handler    products/import-csv
                       :middleware [wrap-multipart-params
                                   wrap-require-auth
                                   #(wrap-require-role % "admin")]}}]
    ["/all" {:delete {:handler    products/delete-all-products
                      :middleware [wrap-require-auth
                                  #(wrap-require-role % "admin")]}}]
    ["/:id" {:get    products/get-product
             :put    {:handler    products/update-product
                      :middleware [wrap-require-auth
                                  #(wrap-require-role % "admin")]}
             :delete {:handler    products/delete-product
                      :middleware [wrap-require-auth
                                  #(wrap-require-role % "admin")]}}]]

   ["/orders"
    ["" {:get  {:handler    orders/list-orders
                :middleware [wrap-require-auth]}
         :post {:handler    orders/create-order
                :middleware [wrap-require-auth
                            #(wrap-require-role % "admin" "buyer")]}}]
    ["/:id" {:get {:handler    orders/get-order
                   :middleware [wrap-require-auth]}}]]])

(def ^:private index-html
  (delay (slurp (clojure.java.io/resource "public/index.html"))))

(defn create-app []
  (-> (ring/ring-handler
       (ring/router
        [api-routes]
        {:conflicts nil
         :data      {:muuntaja   m/instance
                     :middleware [wrap-exception
                                  wrap-params
                                  wrap-keyword-params
                                  muuntaja/format-middleware
                                  wrap-auth]}})
       (ring/routes
        (ring/create-resource-handler {:path "/"})
        (ring/create-default-handler
         {:not-found (fn [_req]
                       {:status 200
                        :headers {"Content-Type" "text/html"}
                        :body @index-html})})))
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :post :put :delete :options]
                 :access-control-allow-headers [:content-type :authorization])))
