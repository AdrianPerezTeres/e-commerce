(ns ecommerce.views.app
  (:require [re-frame.core :as rf]
            [ecommerce.routes :as routes]
            [ecommerce.views.products :as products]
            [ecommerce.views.cart :as cart]
            [ecommerce.views.orders :as orders]
            [ecommerce.views.admin :as admin]
            [ecommerce.views.checkout :as checkout]
            [ecommerce.views.import :as import-view]
            [ecommerce.views.login :as login]))

(defn role-badge [role]
  (let [colors (case role
                 "admin"  "bg-red-100 text-red-700"
                 "buyer"  "bg-green-100 text-green-700"
                 "reader" "bg-blue-100 text-blue-700"
                 "bg-gray-100 text-gray-700")]
    [:span {:class (str "px-2 py-0.5 rounded text-xs font-semibold uppercase " colors)}
     role]))

(defn nav-link [route-name label]
  (let [current-route @(rf/subscribe [:current-route])
        active?       (= route-name (get-in current-route [:data :name]))]
    [:a {:href  (routes/href route-name)
         :class (str "px-4 py-2 rounded-md text-sm font-medium transition-colors "
                     (if active?
                       "bg-white/20 text-white"
                       "text-indigo-100 hover:bg-white/10 hover:text-white"))}
     label]))

(defn navbar []
  (let [cart-count     @(rf/subscribe [:cart-count])
        authenticated? @(rf/subscribe [:authenticated?])
        user           @(rf/subscribe [:auth-user])
        role           (or (:role user) "")]
    [:nav {:class "bg-brand-700 shadow-lg"}
     [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8"}
      [:div {:class "flex items-center justify-between h-16"}
       [:div {:class "flex items-center space-x-4"}
        [:a {:href (routes/href :home)
             :class "text-xl font-bold text-white"}
         "E-Commerce"]
        [nav-link :products "Products"]
        (when (#{"admin"} role)
          [nav-link :admin-products "Manage"])
        (when (#{"admin"} role)
          [nav-link :import "Import"])
        [nav-link :orders "Orders"]]
       [:div {:class "flex items-center space-x-3"}
        (when (#{"admin" "buyer"} role)
          [:a {:href (routes/href :cart)
               :class "relative text-indigo-100 hover:text-white px-3 py-2"}
           "Cart"
           (when (> cart-count 0)
             [:span {:class "absolute -top-1 -right-1 bg-white text-brand-700 text-xs rounded-full h-5 w-5 flex items-center justify-center font-bold"}
              cart-count])])
        (if authenticated?
          [:div {:class "flex items-center space-x-2"}
           [role-badge role]
           [:span {:class "text-indigo-100 text-sm"} (:username user)]
           [:button {:class    "text-indigo-200 hover:text-white text-sm px-2 py-1 rounded hover:bg-white/10 transition-colors"
                     :on-click #(do (rf/dispatch [:logout])
                                    (set! (.-hash js/location) "#/login"))}
            "Logout"]]
          [:a {:href  (routes/href :login)
               :class "text-indigo-100 hover:text-white text-sm px-3 py-2"}
           "Login"])]]]]))

(defn notification-bar []
  (let [notification @(rf/subscribe [:notification])]
    (when notification
      (js/setTimeout #(rf/dispatch [:clear-notification]) 4000)
      [:div {:class (str "fixed top-4 right-4 z-50 px-6 py-3 rounded-lg shadow-lg text-white cursor-pointer "
                         (case (:type notification)
                           :success "bg-green-600"
                           :error   "bg-red-600"
                           "bg-blue-600"))
             :on-click #(rf/dispatch [:clear-notification])}
       (:message notification)])))

(defn current-page []
  (let [route @(rf/subscribe [:current-route])
        page-name (get-in route [:data :name])]
    (case page-name
      :login           [login/login-page]
      :home            [:div {:class "text-center py-20"}
                        [:h1 {:class "text-4xl font-bold text-gray-900"} "Welcome to E-Commerce"]
                        [:p {:class "mt-4 text-lg text-gray-500"} "Browse products, manage inventory, and place orders."]
                        [:a {:href  (routes/href :products)
                             :class "mt-8 inline-block px-8 py-3 bg-brand-600 text-white rounded-full hover:bg-brand-700 transition-colors text-lg font-medium shadow-md"}
                         "Browse Products"]]
      :products        [products/products-page]
      :product-detail  [products/products-page]
      :cart            [cart/cart-page]
      :checkout        [checkout/checkout-page]
      :orders          [orders/orders-page]
      :admin-products  [admin/admin-page]
      :import          [import-view/import-page]
      [:div {:class "text-center py-20"}
       [:h1 {:class "text-4xl font-bold text-gray-900"} "Welcome to E-Commerce"]])))

(defn main-panel []
  [:div {:class "min-h-screen bg-gray-50"}
   [notification-bar]
   [navbar]
   [:main {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8"}
    [current-page]]])
