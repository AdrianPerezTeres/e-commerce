(ns ecommerce.views.products
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [ecommerce.routes :as routes]))

(def categories
  ["Accessories" "Beauty" "Books" "Clothing" "Electronics"
   "Food & Beverage" "Footwear" "Games" "Gifts" "Health"
   "Home & Office" "Kitchen" "Outdoors" "Pets" "Sports"
   "Stationery" "Tools"])

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
        (for [cat categories]
          ^{:key cat}
          [:option {:value cat} cat])]
       [:button {:class    "px-6 py-2 bg-brand-600 text-white rounded-full hover:bg-brand-700 transition-colors font-medium shadow-sm"
                 :on-click #(rf/dispatch [:fetch-products {:q @query :category @category}])}
        "Search"]])))

(defn category-badge [category]
  (let [colors (case category
                 "Electronics"     "bg-blue-50 text-blue-700"
                 "Sports"          "bg-green-50 text-green-700"
                 "Clothing"        "bg-purple-50 text-purple-700"
                 "Footwear"        "bg-orange-50 text-orange-700"
                 "Food & Beverage" "bg-yellow-50 text-yellow-700"
                 "Home & Office"   "bg-indigo-50 text-indigo-700"
                 "Kitchen"         "bg-amber-50 text-amber-700"
                 "Beauty"          "bg-pink-50 text-pink-700"
                 "Books"           "bg-slate-50 text-slate-700"
                 "Games"           "bg-violet-50 text-violet-700"
                 "Outdoors"        "bg-emerald-50 text-emerald-700"
                 "Health"          "bg-red-50 text-red-700"
                 "Pets"            "bg-cyan-50 text-cyan-700"
                 "Accessories"     "bg-rose-50 text-rose-700"
                 "Stationery"      "bg-teal-50 text-teal-700"
                 "Tools"           "bg-gray-100 text-gray-700"
                 "Gifts"           "bg-fuchsia-50 text-fuchsia-700"
                 "bg-gray-50 text-gray-600")]
    [:span {:class (str "px-2 py-0.5 rounded-full text-xs font-medium whitespace-nowrap " colors)}
     (or category "—")]))

(defn stock-badge [stock]
  (if (> stock 0)
    [:span {:class "inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-50 text-green-700"}
     (str stock " in stock")]
    [:span {:class "inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-red-50 text-red-600"}
     "Out of stock"]))

(defn product-row [product]
  (let [qty (r/atom 1)]
    (fn [product]
      (let [role     @(rf/subscribe [:auth-role])
            can-buy? (#{"admin" "buyer"} role)
            in-stock? (> (:stock product) 0)]
        [:tr {:class "border-b border-gray-100 hover:bg-gray-50 transition-colors"}
         [:td {:class "px-4 py-3"}
          [:div
           [:span {:class "text-sm font-medium text-gray-900"} (:name product)]
           [:p {:class "text-xs text-gray-400 mt-0.5 line-clamp-1 max-w-xs"} (:description product)]]]
         [:td {:class "px-4 py-3"}
          [:span {:class "text-xs font-mono text-gray-500"} (:sku product)]]
         [:td {:class "px-4 py-3"}
          [category-badge (:category product)]]
         [:td {:class "px-4 py-3 text-sm font-semibold text-brand-600"} (str "$" (:price product))]
         [:td {:class "px-4 py-3"} [stock-badge (:stock product)]]
         [:td {:class "px-4 py-3 text-xs text-gray-400"} (when (:weight-kg product) (str (:weight-kg product) " kg"))]
         [:td {:class "px-4 py-3"}
          (when (and can-buy? in-stock?)
            [:div {:class "flex items-center gap-2"}
             [:input {:type      "number"
                      :min       1
                      :max       (:stock product)
                      :value     @qty
                      :class     "w-14 px-2 py-1 bg-white border border-gray-300 rounded text-gray-900 text-center text-sm focus:outline-none focus:ring-1 focus:ring-brand-500"
                      :on-change #(reset! qty (js/parseInt (-> % .-target .-value)))}]
             [:button {:class    "px-3 py-1 bg-brand-600 text-white rounded-lg hover:bg-brand-700 transition-colors text-xs font-medium"
                       :on-click #(rf/dispatch [:add-to-cart product @qty])}
              "Add"]])]]))))

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
         [:div {:class "overflow-x-auto bg-white rounded-xl border border-gray-200 shadow-sm"}
          [:table {:class "w-full"}
           [:thead
            [:tr {:class "border-b border-gray-200 text-left bg-gray-50"}
             [:th {:class "px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"} "Product"]
             [:th {:class "px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"} "SKU"]
             [:th {:class "px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"} "Category"]
             [:th {:class "px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"} "Price"]
             [:th {:class "px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"} "Stock"]
             [:th {:class "px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"} "Weight"]
             [:th {:class "px-4 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"} ""]]]
           [:tbody
            (for [product products]
              ^{:key (:id product)}
              [product-row product])]]])])))
