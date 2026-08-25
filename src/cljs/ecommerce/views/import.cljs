(ns ecommerce.views.import
  (:require [re-frame.core :as rf]
            [reagent.core :as r]))

(defn- confirm-modal [show? on-confirm on-cancel]
  (when @show?
    [:div {:class "fixed inset-0 z-50 flex items-center justify-center"}
     ;; backdrop
     [:div {:class "absolute inset-0 bg-black/40 backdrop-blur-sm"
            :on-click on-cancel}]
     ;; dialog
     [:div {:class "relative bg-white rounded-xl shadow-xl border border-gray-200 p-6 max-w-md w-full mx-4"}
      [:div {:class "flex items-center gap-3 mb-4"}
       [:div {:class "flex-shrink-0 w-10 h-10 rounded-full bg-red-100 flex items-center justify-center"}
        [:svg {:class "w-5 h-5 text-red-600" :fill "none" :viewBox "0 0 24 24" :stroke-width "2" :stroke "currentColor"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z"}]]]
       [:h3 {:class "text-lg font-semibold text-gray-900"} "Delete All Products"]]
      [:p {:class "text-sm text-gray-500 mb-6"}
       "This will permanently delete all products from the database. You can re-import them from CSV after."]
      [:div {:class "flex justify-end gap-3"}
       [:button {:class    "px-5 py-2 rounded-full font-medium text-gray-700 border border-gray-300 hover:bg-gray-50 transition-colors"
                 :on-click on-cancel}
        "Cancel"]
       [:button {:class    "px-5 py-2 rounded-full font-medium bg-red-600 text-white hover:bg-red-700 transition-colors shadow-sm"
                 :on-click on-confirm}
        "Delete All"]]]]))

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
        [:h4 {:class "text-sm font-medium text-orange-600 mb-2"} "Duplicates:"]
        [:p {:class "text-sm text-gray-500"}
         (str (count (:duplicates result)) " duplicate SKUs were skipped (already in database)")]])

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
                                (case (:type threat)
                                  "XSS"                "bg-red-100 text-red-700"
                                  "SQL Injection"      "bg-orange-100 text-orange-700"
                                  "Formula Injection"  "bg-yellow-100 text-yellow-700"
                                  "bg-gray-100 text-gray-700"))}
             (:type threat)]
            [:span {:class "text-gray-500"}
             (str "Line " (:line threat) ", " (:field threat) " — " (:detail threat))]])]])]))

(defn import-page []
  (let [file-ref    (r/atom nil)
        show-modal? (r/atom false)]
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
                (if loading "Importing..." "Import")]
               [:button {:class    "px-6 py-2 rounded-full font-medium transition-colors shadow-sm border border-red-300 text-red-600 hover:bg-red-50"
                         :on-click #(reset! show-modal? true)}
                "Clear All Products"]]]
             [confirm-modal show-modal?
              (fn []
                (reset! show-modal? false)
                (rf/dispatch [:delete-all-products]))
              #(reset! show-modal? false)]
             [import-result-panel result]]))))))
