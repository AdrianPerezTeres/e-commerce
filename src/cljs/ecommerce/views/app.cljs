(ns ecommerce.views.app
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
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
         :class "px-4 py-2 rounded-md text-sm font-medium transition-colors focus:outline-none text-indigo-100 hover:bg-white/10 hover:text-white"}
     label]))

(defn about-dialog [on-close]
  [:div {:class    "fixed inset-0 z-50 flex items-center justify-center bg-black/40"
         :on-click (fn [e]
                     (when (= (.-target e) (.-currentTarget e))
                       (on-close)))}
   [:div {:class "bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 overflow-hidden"}
    [:div {:class "flex flex-col items-center pt-8 pb-4 px-6"}
     [:img {:src   "/img/ap-logo.jpg"
            :alt   "Logo"
            :class "h-20 w-20 rounded-2xl mb-4"}]
     [:h2 {:class "text-xl font-bold text-gray-900"} "E-Commerce"]
     [:p {:class "text-xs text-gray-400 mt-1"} "v1.0.0"]]
    [:hr {:class "mx-6 border-gray-200"}]
    [:div {:class "px-6 py-4 text-center"}
     [:p {:class "text-sm font-semibold text-gray-700"} "Code Challenge"]
     [:p {:class "text-xs text-gray-400 mt-2 leading-relaxed"}
      "Full-stack e-commerce application built with Clojure, ClojureScript, "
      "Reagent, Re-frame, and PostgreSQL. Features product management, CSV import, "
      "search, shopping cart, and order processing."]]
    [:hr {:class "mx-6 border-gray-200"}]
    [:div {:class "px-6 py-4 flex items-center justify-center gap-8"}
     [:img {:src   "/img/me.jpg"
            :alt   "Adrian Perez"
            :class "h-14 w-14 rounded-full object-cover flex-shrink-0"}]
     [:div
      [:a {:href   "https://www.linkedin.com/in/adrian-perez-20b384a/"
           :target "_blank"
           :rel    "noopener noreferrer"
           :class  "text-sm font-semibold text-brand-600 hover:underline"}
       "Adrian Perez"]
      [:p {:class "text-xs text-gray-500 mt-0.5"} "Applied Programming LLC"]
      [:a {:href  "mailto:adrian@appliedprogramming.io"
           :class "text-xs text-brand-600 hover:underline"} "adrian@appliedprogramming.io"]]]
    [:div {:class "px-6 pb-1 text-center"}
     [:p {:class "text-xs text-gray-400"} "\u00A9 2026 All rights reserved."]]
    [:hr {:class "mx-6 border-gray-200"}]
    [:div {:class "px-6 py-4 flex justify-end"}
     [:button {:class    "px-4 py-1.5 text-sm font-medium text-gray-600 bg-white border border-gray-300 rounded-md hover:bg-gray-100 transition-colors"
               :on-click on-close}
      "Close"]]]])

(defn avatar-menu [user role]
  (let [open?       (r/atom false)
        show-about? (r/atom false)]
    (fn [user role]
      [:div {:class "relative"}
       [:button {:class    "h-9 w-9 rounded-full overflow-hidden ring-2 ring-white/50 hover:ring-white transition-all focus:outline-none"
                 :on-click #(swap! open? not)}
        [:img {:src   (if (= role "admin")
                        "/img/admin-avatar.jpg"
                        "/img/default-avatar.png")
               :alt   (:username user)
               :class "h-full w-full object-cover"}]]
       (when @open?
         [:div {:class "absolute right-0 mt-2 w-64 bg-white rounded-lg shadow-lg ring-1 ring-black/5 py-1 z-50"
                :on-click #(reset! open? false)}
          [:div {:class "px-4 py-3 flex items-center gap-3"}
           [:img {:src   (if (= role "admin") "/img/admin-avatar.jpg" "/img/default-avatar.png")
                  :alt   "Avatar"
                  :class "h-10 w-10 rounded-full object-cover flex-shrink-0"}]
           [:div
            [:p {:class "text-sm font-medium text-gray-900"} "Adrian Perez"]
            [:p {:class "text-xs text-gray-400"} "adrian@appliedprogramming.io"]]]
          [:div {:class "px-4 pb-2"}
           [role-badge role]]
                [:hr {:class "my-1 border-gray-200"}]
          [:button {:class    "w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 transition-colors"
                    :on-click #(reset! show-about? true)}
           "About"]
          [:button {:class    "w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 transition-colors"
                    :on-click #(do (rf/dispatch [:logout])
                                   (set! (.-hash js/location) "#/login"))}
           "Sign Out"]])
       (when @show-about?
         [about-dialog #(reset! show-about? false)])])))

(defn navbar []
  (let [cart-count     @(rf/subscribe [:cart-count])
        authenticated? @(rf/subscribe [:authenticated?])
        user           @(rf/subscribe [:auth-user])
        role           (or (:role user) "")]
    [:nav {:class "bg-brand-700 shadow-lg"}
     [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8"}
      [:div {:class "flex items-center justify-between h-16"}
       [:a {:href (routes/href :home)
            :class "flex items-center space-x-2 text-xl font-bold text-white focus:outline-none"}
        [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-7 w-7" :fill "none"
               :viewBox "0 0 24 24" :stroke "currentColor" :stroke-width "1.5"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round"
                 :d "M15.75 10.5V6a3.75 3.75 0 1 0-7.5 0v4.5m11.356-1.993 1.263 12c.07.665-.45 1.243-1.119 1.243H4.25a1.125 1.125 0 0 1-1.12-1.243l1.264-12A1.125 1.125 0 0 1 5.513 7.5h12.974c.576 0 1.059.435 1.119 1.007ZM8.625 10.5a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm7.5 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z"}]]
        [:span "E-Commerce"]]
       [:div {:class "flex items-center space-x-1"}
        [nav-link :products "Products"]
        (when (#{"admin"} role)
          [nav-link :admin-products "Manage"])
        (when (#{"admin"} role)
          [nav-link :import "Import"])]
       [:div {:class "flex items-center space-x-3"}
        (if authenticated?
          [avatar-menu user role]
          [:a {:href  (routes/href :login)
               :class "text-indigo-100 hover:text-white text-sm px-3 py-2 focus:outline-none"}
           "Login"])
        [nav-link :orders "Orders"]
        (when (#{"admin" "buyer"} role)
          [:a {:href  (routes/href :cart)
               :class "relative text-indigo-100 hover:text-white p-2 group focus:outline-none"}
           [:svg {:xmlns "http://www.w3.org/2000/svg" :class "h-6 w-6" :fill "none"
                  :viewBox "0 0 24 24" :stroke "currentColor" :stroke-width "1.5"}
            [:path {:stroke-linecap "round" :stroke-linejoin "round"
                    :d "M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 0 0-16.536-1.84M7.5 14.25 5.106 5.272M6 20.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z"}]]
           (when (> cart-count 0)
             [:span {:class "absolute -top-1 -right-1 bg-orange-500 text-white text-xs font-bold rounded-full h-5 w-5 flex items-center justify-center"}
              cart-count])])]]]]))

(defn notification-bar []
  (let [notification @(rf/subscribe [:notification])]
    (when notification
      (js/setTimeout #(rf/dispatch [:clear-notification]) 4000)
      [:div {:class (str "fixed bottom-6 left-1/2 -translate-x-1/2 z-50 px-6 py-3 rounded-lg shadow-lg text-white cursor-pointer "
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
