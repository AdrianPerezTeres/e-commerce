(ns ecommerce.views.login
  (:require [re-frame.core :as rf]
            [reagent.core :as r]))

(def demo-accounts
  [{:username "admin"  :password "admin123"  :role "admin"
    :description "Full access: manage products, import CSV, place orders"}
   {:username "buyer"  :password "buyer123"  :role "buyer"
    :description "Can browse products, add to cart, and place orders"}
   {:username "reader" :password "reader123" :role "reader"
    :description "Read-only: can browse products and view orders"}])

(defn role-badge [role]
  (let [colors (case role
                 "admin"  "bg-red-100 text-red-700"
                 "buyer"  "bg-green-100 text-green-700"
                 "reader" "bg-blue-100 text-blue-700"
                 "bg-gray-100 text-gray-700")]
    [:span {:class (str "px-2 py-0.5 rounded text-xs font-semibold uppercase " colors)}
     role]))

(defn quick-login-card [{:keys [username password role description]}]
  (let [loading? @(rf/subscribe [:auth-loading])]
    [:button
     {:class    (str "w-full text-left p-4 rounded-lg border border-gray-200 "
                     "bg-white hover:bg-brand-50 hover:border-brand-300 "
                     "transition-all cursor-pointer group shadow-sm")
      :disabled loading?
      :on-click #(rf/dispatch [:login {:username username :password password}])}
     [:div {:class "flex items-center justify-between mb-2"}
      [:span {:class "text-gray-900 font-medium group-hover:text-brand-700"} username]
      [role-badge role]]
     [:p {:class "text-sm text-gray-500"} description]
     [:p {:class "text-xs text-gray-400 mt-1"} (str "Password: " password)]]))

(defn login-form []
  (let [form (r/atom {:username "" :password ""})]
    (fn []
      (let [loading? @(rf/subscribe [:auth-loading])
            error    @(rf/subscribe [:auth-error])]
        [:div {:class "bg-white rounded-lg border border-gray-200 p-6 shadow-sm"}
         [:h2 {:class "text-lg font-semibold text-gray-900 mb-4"} "Manual Login"]
         (when error
           [:div {:class "mb-4 p-3 bg-red-50 border border-red-200 rounded text-red-600 text-sm"}
            error])
         [:div {:class "space-y-4"}
          [:div
           [:label {:class "block text-sm text-gray-600 mb-1"} "Username"]
           [:input {:type      "text"
                    :class     "w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
                    :value     (:username @form)
                    :on-change #(swap! form assoc :username (.. % -target -value))}]]
          [:div
           [:label {:class "block text-sm text-gray-600 mb-1"} "Password"]
           [:input {:type      "password"
                    :class     "w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
                    :value     (:password @form)
                    :on-change #(swap! form assoc :password (.. % -target -value))
                    :on-key-down (fn [e]
                                   (when (= "Enter" (.-key e))
                                     (rf/dispatch [:login @form])))}]]
          [:button
           {:class    (str "w-full py-2 rounded-full font-medium transition-colors shadow-sm "
                           (if loading?
                             "bg-gray-300 text-gray-500 cursor-not-allowed"
                             "bg-brand-600 text-white hover:bg-brand-700"))
            :disabled loading?
            :on-click #(rf/dispatch [:login @form])}
           (if loading? "Signing in..." "Sign In")]]]))))

(defn login-page []
  (let [authenticated? @(rf/subscribe [:authenticated?])]
    (when authenticated?
      (set! (.-hash js/location) "#/"))
    [:div {:class "max-w-2xl mx-auto py-12"}
     [:div {:class "text-center mb-8"}
      [:h1 {:class "text-3xl font-bold text-gray-900"} "E-Commerce Login"]
      [:p {:class "mt-2 text-gray-500"} "Sign in to access the store"]]

     [:div {:class "mb-8"}
      [:h2 {:class "text-lg font-semibold text-gray-800 mb-4"} "Quick Login (Demo Accounts)"]
      [:div {:class "space-y-3"}
       (for [account demo-accounts]
         ^{:key (:username account)}
         [quick-login-card account])]]

     [login-form]]))
