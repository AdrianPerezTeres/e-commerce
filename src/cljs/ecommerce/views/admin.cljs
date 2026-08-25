
(ns ecommerce.views.admin
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [ecommerce.views.products :refer [pagination-controls calc-per-page admin-row-height]]))

(def empty-form
  {:name "" :sku "" :description "" :category "" :price "" :stock "" :weight-kg ""})

(defn product-form [initial on-submit submit-label]
  (let [form (r/atom (or initial empty-form))]
    (fn [_ _ _]
      [:div {:class "bg-white rounded-xl border border-gray-200 p-6 mb-6 shadow-sm"}
       [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-4"}
        (doall
         (for [[field label type] [[:name "Name" "text"]
                                    [:sku "SKU" "text"]
                                    [:description "Description" "text"]
                                    [:category "Category" "text"]
                                    [:price "Price" "number"]
                                    [:stock "Stock" "number"]
                                    [:weight-kg "Weight (kg)" "number"]]]
           ^{:key field}
           [:div
            [:label {:class "block text-sm font-medium text-gray-600 mb-1"} label]
            [:input {:type      type
                     :class     "w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-brand-500"
                     :value     (get @form field "")
                     :on-change #(swap! form assoc field (-> % .-target .-value))}]]))]
       [:button {:class    "mt-4 px-6 py-2 bg-brand-600 text-white rounded-full hover:bg-brand-700 transition-colors font-medium shadow-sm"
                 :on-click #(do (on-submit @form)
                                (reset! form empty-form))}
        submit-label]])))

(defn product-table-row [product on-edit on-delete]
  [:tr {:class "border-b border-gray-100 hover:bg-gray-50"}
   [:td {:class "px-4 py-3 text-gray-900"} (:name product)]
   [:td {:class "px-4 py-3 text-gray-500 text-sm"} (:sku product)]
   [:td {:class "px-4 py-3 text-gray-500 text-sm"} (:category product)]
   [:td {:class "px-4 py-3 text-brand-600 font-medium"} (str "$" (:price product))]
   [:td {:class "px-4 py-3 text-gray-500"} (:stock product)]
   [:td {:class "px-4 py-3"}
    [:div {:class "flex gap-2"}
     [:button {:class    "px-3 py-1 text-sm bg-gray-100 text-gray-700 rounded hover:bg-gray-200 transition-colors"
               :on-click #(on-edit product)}
      "Edit"]
     [:button {:class    "px-3 py-1 text-sm bg-red-50 text-red-600 rounded hover:bg-red-100 transition-colors"
               :on-click #(on-delete (:id product))}
      "Delete"]]]])

(defn admin-page []
  (let [editing    (r/atom nil)
        show-add   (r/atom false)
        area-ref   (atom nil)
        resize-raf (atom nil)
        measure!   (fn []
                     (when-let [el @area-ref]
                       (let [h  (.-clientHeight el)
                             pp (calc-per-page h admin-row-height)]
                         (when (pos? pp)
                           (rf/dispatch [:fetch-products {:per-page pp :page 1}])))))
        on-resize  (fn []
                     (when-let [raf @resize-raf] (js/cancelAnimationFrame raf))
                     (reset! resize-raf (js/requestAnimationFrame measure!)))]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (js/requestAnimationFrame measure!)
        (.addEventListener js/window "resize" on-resize))
      :component-will-unmount
      (fn [_]
        (.removeEventListener js/window "resize" on-resize)
        (when-let [raf @resize-raf] (js/cancelAnimationFrame raf)))
      :reagent-render
      (fn []
        (let [role @(rf/subscribe [:auth-role])]
          (if (not= role "admin")
            [:div {:class "text-center py-20"}
             [:h2 {:class "text-2xl font-bold text-gray-400"} "Access Denied"]
             [:p {:class "mt-2 text-gray-500"} "Only admin users can manage products."]]
            (let [products @(rf/subscribe [:products])
                  loading  @(rf/subscribe [:products-loading])]
              [:div {:class "flex flex-col flex-1 min-h-0"}
               [:div {:class "flex items-center justify-between mb-4"}
                [:h1 {:class "text-3xl font-bold text-gray-900"} "Manage Products"]
                [:button {:class    "px-6 py-2 bg-brand-600 text-white rounded-full hover:bg-brand-700 transition-colors font-medium shadow-sm"
                          :on-click #(swap! show-add not)}
                 (if @show-add "Cancel" "Add Product")]]

               (when @show-add
                 [product-form nil
                  (fn [data]
                    (rf/dispatch [:create-product data])
                    (reset! show-add false))
                  "Create Product"])

               (when @editing
                 [:div {:class "mb-4"}
                  [:h2 {:class "text-xl font-semibold text-gray-800 mb-2"} "Edit Product"]
                  [product-form @editing
                   (fn [data]
                     (rf/dispatch [:update-product (:id @editing) data])
                     (reset! editing nil))
                   "Update Product"]
                  [:button {:class    "px-4 py-2 text-gray-500 hover:text-gray-700 transition-colors"
                            :on-click #(reset! editing nil)}
                   "Cancel editing"]])

               (if loading
                 [:div {:class "text-center py-12 text-gray-400"} "Loading..."]
                 [:div {:class "flex flex-col flex-1 min-h-0"
                        :ref   #(when % (reset! area-ref %))}
                  [:div {:class "overflow-hidden bg-white rounded-xl border border-gray-200 shadow-sm"}
                   [:table {:class "w-full"}
                    [:thead
                     [:tr {:class "border-b border-gray-200 text-left bg-gray-50"}
                      [:th {:class "px-4 py-3 text-sm font-medium text-gray-500"} "Name"]
                      [:th {:class "px-4 py-3 text-sm font-medium text-gray-500"} "SKU"]
                      [:th {:class "px-4 py-3 text-sm font-medium text-gray-500"} "Category"]
                      [:th {:class "px-4 py-3 text-sm font-medium text-gray-500"} "Price"]
                      [:th {:class "px-4 py-3 text-sm font-medium text-gray-500"} "Stock"]
                      [:th {:class "px-4 py-3 text-sm font-medium text-gray-500"} "Actions"]]]
                    [:tbody
                     (for [product products]
                       ^{:key (:id product)}
                       [product-table-row product
                        #(reset! editing %)
                        #(rf/dispatch [:delete-product %])])]]]
                  [pagination-controls]])]))))
      })))
