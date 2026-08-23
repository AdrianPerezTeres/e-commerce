(ns ecommerce.views.checkout
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn order-summary [items total]
  [:div {:class "bg-white rounded-lg border border-gray-200 p-6 shadow-sm"}
   [:h2 {:class "text-lg font-semibold text-gray-900 mb-4"} "Order Summary"]
   [:div {:class "space-y-3 mb-4"}
    (for [item items]
      ^{:key (:id item)}
      [:div {:class "flex justify-between text-sm"}
       [:span {:class "text-gray-600"}
        (str (:name item) " x " (:quantity item))]
       [:span {:class "text-gray-900"}
        (str "$" (.toFixed (* (:price item) (:quantity item)) 2))]])]
   [:div {:class "border-t border-gray-200 pt-3 flex justify-between"}
    [:span {:class "text-gray-600 font-medium"} "Total"]
    [:span {:class "text-xl font-bold text-brand-600"} (str "$" (.toFixed total 2))]]])

(defn checkout-page []
  (let [form     (r/atom {:card-number "" :card-name "" :expiry "" :cvv ""})
        paying?  (r/atom false)
        errors   (r/atom {})]
    (fn []
      (let [items @(rf/subscribe [:cart-items])
            total @(rf/subscribe [:cart-total])]
        (if (empty? items)
          [:div {:class "text-center py-12"}
           [:p {:class "text-gray-400 text-lg"} "Nothing to checkout."]
           [:a {:href  "#/products"
                :class "mt-4 inline-block px-6 py-2 bg-brand-600 text-white rounded-full hover:bg-brand-700 transition-colors shadow-sm"}
            "Browse Products"]]
          [:div {:class "max-w-2xl mx-auto"}
           [:h1 {:class "text-3xl font-bold text-gray-900 mb-6"} "Checkout"]
           [:div {:class "space-y-6"}
            [order-summary items total]
            [:div {:class "bg-white rounded-lg border border-gray-200 p-6 shadow-sm"}
             [:h2 {:class "text-lg font-semibold text-gray-900 mb-4"} "Payment Details"]
             [:p {:class "text-xs text-amber-600 mb-4 bg-amber-50 px-3 py-2 rounded"} "This is a demo — no real payment is processed."]
             [:div {:class "space-y-4"}
              [:div
               [:label {:class "block text-sm text-gray-600 mb-1"} "Cardholder Name"]
               [:input {:class       "w-full bg-white border border-gray-300 rounded-lg px-4 py-2 text-gray-900 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                        :type        "text"
                        :placeholder "John Doe"
                        :value       (:card-name @form)
                        :on-change   #(do (swap! form assoc :card-name (.. % -target -value))
                                          (swap! errors dissoc :card-name))}]
               (when (:card-name @errors)
                 [:p {:class "text-red-500 text-xs mt-1"} (:card-name @errors)])]
              [:div
               [:label {:class "block text-sm text-gray-600 mb-1"} "Card Number"]
               [:input {:class       "w-full bg-white border border-gray-300 rounded-lg px-4 py-2 text-gray-900 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                        :type        "text"
                        :placeholder "4242 4242 4242 4242"
                        :max-length  19
                        :value       (:card-number @form)
                        :on-change   #(let [v (.. % -target -value)
                                            digits (str/replace v #"[^0-9]" "")
                                            formatted (->> (partition-all 4 digits)
                                                           (map (partial apply str))
                                                           (str/join " "))]
                                       (swap! form assoc :card-number formatted)
                                       (swap! errors dissoc :card-number))}]
               (when (:card-number @errors)
                 [:p {:class "text-red-500 text-xs mt-1"} (:card-number @errors)])]
              [:div {:class "grid grid-cols-2 gap-4"}
               [:div
                [:label {:class "block text-sm text-gray-600 mb-1"} "Expiry"]
                [:input {:class       "w-full bg-white border border-gray-300 rounded-lg px-4 py-2 text-gray-900 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                         :type        "text"
                         :placeholder "MM/YY"
                         :max-length  5
                         :value       (:expiry @form)
                         :on-change   #(let [v (.. % -target -value)
                                             clean (str/replace v #"[^0-9/]" "")
                                             formatted (if (and (= 2 (count clean))
                                                                (not (str/includes? clean "/"))
                                                                (> (count clean) (count (:expiry @form))))
                                                         (str clean "/")
                                                         clean)]
                                        (swap! form assoc :expiry formatted)
                                        (swap! errors dissoc :expiry))}]
                (when (:expiry @errors)
                  [:p {:class "text-red-500 text-xs mt-1"} (:expiry @errors)])]
               [:div
                [:label {:class "block text-sm text-gray-600 mb-1"} "CVV"]
                [:input {:class       "w-full bg-white border border-gray-300 rounded-lg px-4 py-2 text-gray-900 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                         :type        "text"
                         :placeholder "123"
                         :max-length  4
                         :value       (:cvv @form)
                         :on-change   #(do (swap! form assoc :cvv (str/replace (.. % -target -value) #"[^0-9]" ""))
                                          (swap! errors dissoc :cvv))}]
                (when (:cvv @errors)
                  [:p {:class "text-red-500 text-xs mt-1"} (:cvv @errors)])]]]]]
            [:button
             {:class    (str "w-full py-3 rounded-full font-medium text-lg transition-colors shadow-sm "
                             (if @paying?
                               "bg-gray-300 text-gray-500 cursor-not-allowed"
                               "bg-brand-600 text-white hover:bg-brand-700"))
              :disabled @paying?
              :on-click (fn []
                          (let [errs (cond-> {}
                                       (empty? (:card-name @form))
                                       (assoc :card-name "Name is required")
                                       (< (count (str/replace (:card-number @form) #" " "")) 16)
                                       (assoc :card-number "Enter a valid 16-digit card number")
                                       (not (re-matches #"\d{2}/\d{2}" (:expiry @form)))
                                       (assoc :expiry "Enter a valid expiry (MM/YY)")
                                       (< (count (:cvv @form)) 3)
                                       (assoc :cvv "Enter a valid CVV"))]
                            (if (seq errs)
                              (reset! errors errs)
                              (do
                                (reset! paying? true)
                                (js/setTimeout
                                 (fn []
                                   (rf/dispatch [:place-order])
                                   (reset! paying? false))
                                 1500)))))}
             (if @paying?
               "Processing Payment..."
               (str "Pay $" (.toFixed total 2)))]])))))
