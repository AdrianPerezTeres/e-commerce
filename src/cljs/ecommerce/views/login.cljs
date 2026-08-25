(ns ecommerce.views.login
  (:require [re-frame.core :as rf]))

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
     [:p {:class "text-sm text-gray-500"} description]]))

(defn login-page []
  (let [authenticated? @(rf/subscribe [:authenticated?])]
    (when authenticated?
      (set! (.-hash js/location) "#/"))
    [:div {:class "max-w-2xl mx-auto py-12"}
     [:div {:class "text-center mb-8"}
      [:h1 {:class "text-3xl font-bold text-gray-900"} "Switch Role"]
      [:p {:class "mt-2 text-gray-500"} "Select a role to continue"]]

     [:div {:class "space-y-3"}
      (for [account demo-accounts]
        ^{:key (:username account)}
        [quick-login-card account])]]))
