(ns ecommerce.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub :current-route
  (fn [db _] (:current-route db)))

(rf/reg-sub :products
  (fn [db _] (get-in db [:products :items])))

(rf/reg-sub :products-loading
  (fn [db _] (get-in db [:products :loading])))

(rf/reg-sub :cart-items
  (fn [db _] (get-in db [:cart :items])))

(rf/reg-sub :cart-count
  (fn [db _] (reduce + 0 (map :quantity (get-in db [:cart :items])))))

(rf/reg-sub :cart-total
  (fn [db _]
    (reduce (fn [total item]
              (+ total (* (:price item) (:quantity item))))
            0
            (get-in db [:cart :items]))))

(rf/reg-sub :orders
  (fn [db _] (get-in db [:orders :items])))

(rf/reg-sub :orders-loading
  (fn [db _] (get-in db [:orders :loading])))

(rf/reg-sub :order-detail
  (fn [db [_ order-id]]
    (get-in db [:orders :details order-id])))

(rf/reg-sub :search-query
  (fn [db _] (get-in db [:search :query])))

(rf/reg-sub :search-category
  (fn [db _] (get-in db [:search :category])))

(rf/reg-sub :notification
  (fn [db _] (get-in db [:ui :notification])))

(rf/reg-sub :auth-token
  (fn [db _] (get-in db [:auth :token])))

(rf/reg-sub :authenticated?
  (fn [db _] (some? (get-in db [:auth :token]))))

(rf/reg-sub :auth-user
  (fn [db _] (get-in db [:auth :user])))

(rf/reg-sub :auth-role
  (fn [db _] (get-in db [:auth :user :role])))

(rf/reg-sub :auth-loading
  (fn [db _] (get-in db [:auth :loading])))

(rf/reg-sub :auth-error
  (fn [db _] (get-in db [:auth :error])))

(rf/reg-sub :import-result
  (fn [db _] (get-in db [:import :result])))

(rf/reg-sub :import-loading
  (fn [db _] (get-in db [:import :loading])))
