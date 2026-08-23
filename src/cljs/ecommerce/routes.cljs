(ns ecommerce.routes
  (:require [re-frame.core :as rf]
            [reitit.frontend :as reitit]
            [reitit.frontend.easy :as rfe]))

(def routes
  [["/"         {:name :home}]
   ["/products" {:name :products}]
   ["/products/:id" {:name :product-detail}]
   ["/cart"     {:name :cart}]
   ["/checkout" {:name :checkout}]
   ["/orders"   {:name :orders}]
   ["/admin/products" {:name :admin-products}]
   ["/import"   {:name :import}]
   ["/login"    {:name :login}]])

(defn on-navigate [match _history]
  (when match
    (rf/dispatch [:set-current-route match])))

(defn start! []
  (rfe/start!
   (reitit/router routes)
   on-navigate
   {:use-fragment true}))

(defn href [route-name & [params]]
  (rfe/href route-name params))
