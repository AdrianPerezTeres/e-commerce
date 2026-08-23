(ns ecommerce.views.cart
  (:require [re-frame.core :as rf]))

(defn cart-item [item]
  [:div {:class "flex items-center justify-between bg-white rounded-lg border border-gray-200 p-4"}
   [:div {:class "flex-1"}
    [:h3 {:class "text-gray-900 font-medium"} (:name item)]
    [:p {:class "text-sm text-gray-500"} (str "$" (:price item) " each")]]
   [:div {:class "flex items-center gap-3"}
    [:button {:class    "w-8 h-8 bg-gray-100 text-gray-600 rounded hover:bg-gray-200 transition-colors font-medium"
              :on-click #(rf/dispatch [:update-cart-quantity (:id item) (dec (:quantity item))])}
     "-"]
    [:span {:class "text-gray-900 w-8 text-center font-medium"} (:quantity item)]
    [:button {:class    "w-8 h-8 bg-gray-100 text-gray-600 rounded hover:bg-gray-200 transition-colors font-medium"
              :on-click #(rf/dispatch [:update-cart-quantity (:id item) (inc (:quantity item))])}
     "+"]
    [:span {:class "text-brand-600 font-semibold w-24 text-right"}
     (str "$" (.toFixed (* (:price item) (:quantity item)) 2))]
    [:button {:class    "ml-2 text-red-500 hover:text-red-700 transition-colors text-sm"
              :on-click #(rf/dispatch [:remove-from-cart (:id item)])}
     "Remove"]]])

(defn cart-page []
  (fn []
    (let [items @(rf/subscribe [:cart-items])
          total @(rf/subscribe [:cart-total])]
      [:div
       [:h1 {:class "text-3xl font-bold text-gray-900 mb-6"} "Shopping Cart"]
       (if (empty? items)
         [:div {:class "text-center py-12"}
          [:p {:class "text-gray-400 text-lg"} "Your cart is empty."]
          [:a {:href  "#/products"
               :class "mt-4 inline-block px-6 py-2 bg-brand-600 text-white rounded-full hover:bg-brand-700 transition-colors shadow-sm"}
           "Browse Products"]]
         [:div
          [:div {:class "space-y-3 mb-6"}
           (for [item items]
             ^{:key (:id item)}
             [cart-item item])]
          [:div {:class "bg-white rounded-lg border border-gray-200 p-6 shadow-sm"}
           [:div {:class "flex items-center justify-between mb-4"}
            [:span {:class "text-lg text-gray-600"} "Total"]
            [:span {:class "text-2xl font-bold text-brand-600"} (str "$" (.toFixed total 2))]]
           [:a {:href  "#/checkout"
               :class "block w-full py-3 bg-brand-600 text-white rounded-full hover:bg-brand-700 transition-colors font-medium text-lg text-center shadow-sm"}
           "Proceed to Checkout"]]])])))
