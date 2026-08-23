(ns ecommerce.views.products
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [ecommerce.routes :as routes]))

(defn search-bar []
  (let [query    (r/atom "")
        category (r/atom "")]
    (fn []
      [:div {:class "flex flex-col sm:flex-row gap-4 mb-8"}
       [:input {:type        "text"
                :placeholder "Search products..."
                :class       "flex-1 px-4 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
                :value       @query
                :on-change   #(reset! query (-> % .-target .-value))}]
       [:select {:class     "px-4 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 focus:outline-none focus:ring-2 focus:ring-brand-500"
                 :value     @category
                 :on-change #(reset! category (-> % .-target .-value))}
        [:option {:value ""} "All Categories"]
        [:option {:value "Electronics"} "Electronics"]
        [:option {:value "Clothing"} "Clothing"]
        [:option {:value "Home & Kitchen"} "Home & Kitchen"]
        [:option {:value "Sports"} "Sports"]
        [:option {:value "Books"} "Books"]
        [:option {:value "Toys"} "Toys"]
        [:option {:value "Misc"} "Misc"]]
       [:button {:class    "px-6 py-2 bg-brand-600 text-white rounded-full hover:bg-brand-700 transition-colors font-medium shadow-sm"
                 :on-click #(rf/dispatch [:fetch-products {:q @query :category @category}])}
        "Search"]])))

(defn product-card [product]
  (let [qty (r/atom 1)]
    (fn [product]
      (let [role @(rf/subscribe [:auth-role])
            can-buy? (#{"admin" "buyer"} role)]
        [:div {:class "bg-white rounded-xl border border-gray-200 p-6 flex flex-col justify-between hover:shadow-lg hover:border-brand-300 transition-all"}
         [:div
          [:div {:class "flex justify-between items-start mb-2"}
           [:h3 {:class "text-lg font-semibold text-gray-900"} (:name product)]
           [:span {:class "text-xs bg-brand-50 text-brand-700 px-2 py-1 rounded font-medium"} (:category product)]]
          [:p {:class "text-sm text-gray-500 mb-3 line-clamp-2"} (:description product)]
          [:div {:class "flex items-center justify-between mb-1"}
           [:span {:class "text-sm text-gray-400"} (str "SKU: " (:sku product))]
           [:span {:class "text-sm text-gray-400"} (str (:weight-kg product) " kg")]]]
         [:div {:class "mt-4 pt-4 border-t border-gray-100"}
          [:div {:class "flex items-center justify-between mb-3"}
           [:span {:class "text-2xl font-bold text-brand-600"} (str "$" (:price product))]
           [:span {:class (str "text-sm font-medium "
                               (if (> (:stock product) 0)
                                 "text-green-600" "text-red-500"))}
            (if (> (:stock product) 0)
              (str (:stock product) " in stock")
              "Out of stock")]]
          (when (and can-buy? (> (:stock product) 0))
            [:div {:class "flex items-center gap-2"}
             [:input {:type      "number"
                      :min       1
                      :max       (:stock product)
                      :value     @qty
                      :class     "w-16 px-2 py-1 bg-white border border-gray-300 rounded text-gray-900 text-center focus:outline-none focus:ring-1 focus:ring-brand-500"
                      :on-change #(reset! qty (js/parseInt (-> % .-target .-value)))}]
             [:button {:class    "flex-1 px-4 py-2 bg-brand-600 text-white rounded-lg hover:bg-brand-700 transition-colors text-sm font-medium shadow-sm"
                       :on-click #(rf/dispatch [:add-to-cart product @qty])}
              "Add to Cart"]])]]))))

(defn products-page []
  (rf/dispatch [:fetch-products])
  (fn []
    (let [products @(rf/subscribe [:products])
          loading  @(rf/subscribe [:products-loading])]
      [:div
       [:h1 {:class "text-3xl font-bold text-gray-900 mb-6"} "Products"]
       [search-bar]
       (cond
         loading
         [:div {:class "text-center py-12 text-gray-400"} "Loading products..."]

         (empty? products)
         [:div {:class "text-center py-12 text-gray-400"} "No products found."]

         :else
         [:div {:class "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"}
          (for [product products]
            ^{:key (:id product)}
            [product-card product])])])))
