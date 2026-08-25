(ns ecommerce.views.orders
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [ecommerce.http :as http]))

(defn status-badge [status]
  [:span {:class (str "inline-block px-3 py-1 rounded-full text-xs font-medium "
                      (case status
                        "paid"      "bg-green-100 text-green-700"
                        "pending"   "bg-yellow-100 text-yellow-700"
                        "failed"    "bg-red-100 text-red-700"
                        "cancelled" "bg-gray-100 text-gray-600"
                        "bg-gray-100 text-gray-600"))}
   status])

(defn order-detail-modal [order on-close]
  (let [state (r/atom {:loading true :items []})]
    (http/get! {:uri        (str "/api/orders/" (:id order))
                :on-success (fn [data]
                              (reset! state {:loading false :items (or (:items data) [])}))
                :on-failure (fn [_]
                              (reset! state {:loading false :items []}))})
    (fn [order on-close]
      (let [{:keys [loading items]} @state
            total      (count items)
            show-items (take 5 items)
            display-id (or (:order-number order) (:id order))]
        [:div {:class    "fixed inset-0 z-50 flex items-center justify-center bg-black/40"
               :on-click (fn [e]
                           (when (= (.-target e) (.-currentTarget e))
                             (on-close)))}
         [:div {:class "bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 overflow-hidden"}
          [:div {:class "flex items-center justify-between px-6 py-4 border-b border-gray-200"}
           [:div
            [:h2 {:class "text-lg font-semibold text-gray-900"} "Order Items"]
            [:p {:class "text-xs font-mono text-gray-400 mt-0.5"} display-id]]
           [status-badge (:status order)]]
          [:div {:class "px-6 py-4"}
           (if loading
             [:div {:class "text-center py-8 text-gray-400"} "Loading items..."]
             (if (empty? items)
               [:div {:class "text-center py-8 text-gray-400"} "No items in this order."]
               [:div
                [:table {:class "w-full"}
                 [:thead
                  [:tr {:class "text-left"}
                   [:th {:class "pb-2 text-xs font-medium text-gray-400 uppercase"} "Product"]
                   [:th {:class "pb-2 text-xs font-medium text-gray-400 uppercase text-right"} "Qty"]
                   [:th {:class "pb-2 text-xs font-medium text-gray-400 uppercase text-right"} "Unit Price"]
                   [:th {:class "pb-2 text-xs font-medium text-gray-400 uppercase text-right"} "Subtotal"]]]
                 [:tbody
                  (for [[idx item] (map-indexed vector show-items)]
                    ^{:key idx}
                    [:tr {:class "border-b border-gray-100 last:border-0"}
                     [:td {:class "py-2.5 text-sm text-gray-700"} (:product-name item)]
                     [:td {:class "py-2.5 text-sm text-gray-500 text-right"} (:quantity item)]
                     [:td {:class "py-2.5 text-sm text-gray-500 text-right"}
                      (str "$" (:unit-price item))]
                     [:td {:class "py-2.5 text-sm font-medium text-gray-700 text-right"}
                      (str "$" (.toFixed (* (or (:unit-price item) 0)
                                            (or (:quantity item) 0)) 2))]])]]
                (when (> total 5)
                  [:p {:class "mt-3 text-xs text-gray-400 italic"}
                   (str "Showing 5 of " total " items in this order")])]))]
          [:div {:class "flex items-center justify-between px-6 py-3 bg-gray-50 border-t border-gray-200"}
           [:div
            [:span {:class "text-sm text-gray-500"} (:created-at order)]
            [:span {:class "ml-4 text-lg font-semibold text-brand-600"} (str "Total: $" (:total order))]]
           [:button {:class    "px-4 py-1.5 text-sm font-medium text-gray-600 bg-white border border-gray-300 rounded-full hover:bg-gray-100 transition-colors"
                     :on-click on-close}
            "Close"]]]]))))

(defn order-row [order on-click]
  (let [display-id (or (:order-number order) (:id order))]
    [:div {:class    "bg-white rounded-lg border border-gray-200 mb-3 shadow-sm overflow-hidden cursor-pointer hover:bg-gray-50 transition-colors"
           :on-click #(on-click order)}
     [:div {:class "flex items-center justify-between px-6 py-4"}
      [:div
       [:p {:class "text-sm font-mono text-gray-900"} display-id]
       [:p {:class "text-xs text-gray-400 mt-0.5"} (:created-at order)]]
      [:div {:class "flex items-center gap-4"}
       [status-badge (:status order)]
       [:span {:class "text-lg font-semibold text-brand-600"} (str "$" (:total order))]]]]))

(defn orders-page []
  (let [selected (r/atom nil)]
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
              [order-row order #(reset! selected %)])
            (when @selected
              [order-detail-modal @selected #(reset! selected nil)])])]))))
