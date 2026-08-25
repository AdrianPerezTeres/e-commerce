(ns ecommerce.views.import
  (:require [re-frame.core :as rf]
            [reagent.core :as r]))

(defn import-result-panel [result]
  (when result
    [:div {:class "mt-6 bg-white rounded-xl border border-gray-200 p-6 shadow-sm"}
     [:h3 {:class "text-lg font-semibold text-gray-900 mb-4"} "Import Results"]
     [:div {:class "grid grid-cols-2 md:grid-cols-5 gap-4 mb-4"}
      [:div {:class "bg-green-50 rounded-lg p-4 text-center border border-green-100"}
       [:p {:class "text-2xl font-bold text-green-600"} (:imported result)]
       [:p {:class "text-sm text-gray-500"} "Imported"]]
      [:div {:class "bg-yellow-50 rounded-lg p-4 text-center border border-yellow-100"}
       [:p {:class "text-2xl font-bold text-yellow-600"} (:skipped result)]
       [:p {:class "text-sm text-gray-500"} "Skipped (empty rows)"]]
      [:div {:class "bg-orange-50 rounded-lg p-4 text-center border border-orange-100"}
       [:p {:class "text-2xl font-bold text-orange-600"} (count (:duplicates result))]
       [:p {:class "text-sm text-gray-500"} "Duplicates"]]
      [:div {:class "bg-red-50 rounded-lg p-4 text-center border border-red-100"}
       [:p {:class "text-2xl font-bold text-red-600"} (count (:errors result))]
       [:p {:class "text-sm text-gray-500"} "Errors"]]
      [:div {:class "bg-purple-50 rounded-lg p-4 text-center border border-purple-100"}
       [:p {:class "text-2xl font-bold text-purple-600"} (count (:threats result))]
       [:p {:class "text-sm text-gray-500"} "Threats Blocked"]]]

     (when (seq (:duplicates result))
       [:div {:class "mb-4"}
        [:h4 {:class "text-sm font-medium text-orange-600 mb-2"} "Duplicate SKUs:"]
        [:div {:class "space-y-1"}
         (for [[idx dup] (map-indexed vector (:duplicates result))]
           ^{:key idx}
           [:p {:class "text-sm text-gray-500"}
            (str "Line " (:line dup) ": " (:sku dup) " - " (:reason dup))])]])

     (when (seq (:errors result))
       [:div {:class "mb-4"}
        [:h4 {:class "text-sm font-medium text-red-600 mb-2"} "Errors:"]
        [:div {:class "space-y-1 max-h-48 overflow-y-auto"}
         (for [[idx err] (map-indexed vector (:errors result))]
           ^{:key idx}
           [:p {:class "text-sm text-gray-500"}
            (str "Line " (:line err) ": "
                 (or (:error err) (clojure.string/join ", " (:errors err))))])]])

     (when (seq (:threats result))
       [:div
        [:h4 {:class "text-sm font-medium text-purple-600 mb-2"} "Security Threats Detected & Neutralized:"]
        [:div {:class "space-y-1.5 max-h-48 overflow-y-auto"}
         (for [[idx threat] (map-indexed vector (:threats result))]
           ^{:key idx}
           [:div {:class "flex items-center gap-2 text-sm"}
            [:span {:class (str "inline-block px-2 py-0.5 rounded text-xs font-medium "
                                (if (= (:type threat) "XSS")
                                  "bg-red-100 text-red-700"
                                  "bg-orange-100 text-orange-700"))}
             (:type threat)]
            [:span {:class "text-gray-500"}
             (str "Line " (:line threat) ", " (:field threat) " — " (:detail threat))]])]])]))

(defn import-page []
  (let [file-ref (r/atom nil)]
    (fn []
      (let [role @(rf/subscribe [:auth-role])]
        (if (not= role "admin")
          [:div {:class "text-center py-20"}
           [:h2 {:class "text-2xl font-bold text-gray-400"} "Access Denied"]
           [:p {:class "mt-2 text-gray-500"} "Only admin users can import products."]]
          (let [result  @(rf/subscribe [:import-result])
                loading @(rf/subscribe [:import-loading])]
            [:div
             [:h1 {:class "text-3xl font-bold text-gray-900 mb-6"} "Import Products from CSV"]
             [:div {:class "bg-white rounded-xl border border-gray-200 p-6 shadow-sm"}
              [:p {:class "text-gray-500 mb-4"}
               "Upload a CSV file with columns: name, sku, description, category, price, stock, weight_kg"]
              [:div {:class "flex items-center gap-4"}
               [:input {:type      "file"
                        :accept    ".csv"
                        :class     "text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-full file:border-0 file:bg-brand-600 file:text-white file:cursor-pointer hover:file:bg-brand-700 file:shadow-sm"
                        :on-change #(reset! file-ref (-> % .-target .-files (aget 0)))}]
               [:button {:class    (str "px-6 py-2 rounded-full font-medium transition-colors shadow-sm "
                                        (if loading
                                          "bg-gray-300 text-gray-500 cursor-not-allowed"
                                          "bg-brand-600 text-white hover:bg-brand-700"))
                         :disabled loading
                         :on-click (fn []
                                     (when @file-ref
                                       (let [form-data (js/FormData.)]
                                         (.append form-data "file" @file-ref)
                                         (rf/dispatch [:import-csv form-data]))))}
                (if loading "Importing..." "Import")]]]
             [import-result-panel result]]))))))
