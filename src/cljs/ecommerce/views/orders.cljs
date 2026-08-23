(ns ecommerce.views.orders
  (:require [re-frame.core :as rf]))

(defn order-row [order]
  [:div {:class "bg-white rounded-lg border border-gray-200 p-4 mb-3 shadow-sm"}
   [:div {:class "flex items-center justify-between"}
    [:div
     [:p {:class "text-gray-900 font-medium"} (str "Order #" (subs (str (:id order)) 0 8) "...")]
     [:p {:class "text-sm text-gray-500"} (:created-at order)]]
    [:div {:class "text-right"}
     [:span {:class (str "inline-block px-3 py-1 rounded-full text-xs font-medium "
                         (case (:status order)
                           "paid"      "bg-green-100 text-green-700"
                           "pending"   "bg-yellow-100 text-yellow-700"
                           "failed"    "bg-red-100 text-red-700"
                           "cancelled" "bg-gray-100 text-gray-600"
                           "bg-gray-100 text-gray-600"))}
      (:status order)]
     [:p {:class "text-lg font-semibold text-brand-600 mt-1"} (str "$" (:total order))]]]])

(defn orders-page []
  (rf/dispatch [:fetch-orders])
  (fn []
    (let [orders  @(rf/subscribe [:orders])
          loading @(rf/subscribe [:orders-loading])]
      [:div
       [:h1 {:class "text-3xl font-bold text-gray-900 mb-6"} "Order History"]
       (cond
         loading
         [:div {:class "text-center py-12 text-gray-400"} "Loading orders..."]

         (empty? orders)
         [:div {:class "text-center py-12 text-gray-400"} "No orders yet."]

         :else
         [:div
          (for [order orders]
            ^{:key (:id order)}
            [order-row order])])])))
