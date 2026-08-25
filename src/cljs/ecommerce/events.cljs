(ns ecommerce.events
  (:require [re-frame.core :as rf]
            [ecommerce.db :as db]
            [ecommerce.http :as http]))

(rf/reg-event-db
 :initialize-db
 (fn [_ _]
   db/default-db))

(rf/reg-event-db
 :set-current-route
 (fn [db [_ route]]
   (assoc db :current-route route)))

(rf/reg-event-db
 :set-notification
 (fn [db [_ notification]]
   (assoc-in db [:ui :notification] notification)))

(rf/reg-event-fx
 :clear-notification
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:ui :notification] nil)}))

(rf/reg-event-fx
 :fetch-products
 (fn [{:keys [db]} [_ params]]
   (http/get! {:uri        (str "/api/products?" (some-> params clj->js js/URLSearchParams. .toString))
               :on-success #(rf/dispatch [:products-loaded %])
               :on-failure #(rf/dispatch [:set-notification {:type :error :message "Failed to load products"}])})
   {:db (assoc-in db [:products :loading] true)}))

(rf/reg-event-db
 :products-loaded
 (fn [db [_ products]]
   (-> db
       (assoc-in [:products :items] products)
       (assoc-in [:products :loading] false))))

(rf/reg-event-fx
 :create-product
 (fn [_ [_ product-data]]
   (http/post! {:uri        "/api/products"
                :body       product-data
                :on-success (fn [_]
                              (rf/dispatch [:set-notification {:type :success :message "Product created"}])
                              (rf/dispatch [:fetch-products]))
                :on-failure #(rf/dispatch [:set-notification {:type :error :message (or (:error %) "Failed to create product")}])})
   {}))

(rf/reg-event-fx
 :update-product
 (fn [_ [_ id product-data]]
   (http/put! {:uri        (str "/api/products/" id)
               :body       product-data
               :on-success (fn [_]
                              (rf/dispatch [:set-notification {:type :success :message "Product updated"}])
                              (rf/dispatch [:fetch-products]))
               :on-failure #(rf/dispatch [:set-notification {:type :error :message (or (:error %) "Failed to update product")}])})
   {}))

(rf/reg-event-fx
 :delete-product
 (fn [_ [_ id]]
   (http/delete! {:uri        (str "/api/products/" id)
                  :on-success (fn [_]
                                (rf/dispatch [:set-notification {:type :success :message "Product deleted"}])
                                (rf/dispatch [:fetch-products]))
                  :on-failure #(rf/dispatch [:set-notification {:type :error :message "Failed to delete product"}])})
   {}))

(rf/reg-event-fx
 :delete-all-products
 (fn [_ _]
   (http/delete! {:uri        "/api/products/all"
                  :on-success (fn [result]
                                (rf/dispatch [:set-notification {:type :success
                                                                 :message (str "Deleted " (:deleted result) " products")}])
                                (rf/dispatch [:fetch-products]))
                  :on-failure #(rf/dispatch [:set-notification {:type :error :message "Failed to delete products"}])})
   {}))

(rf/reg-event-fx
 :import-csv
 (fn [{:keys [db]} [_ form-data]]
   (http/post! {:uri        "/api/products/import"
                :body       form-data
                :multipart? true
                :on-success #(rf/dispatch [:import-complete %])
                :on-failure (fn [err]
                              (rf/dispatch [:set-notification {:type :error :message (or (:error err) "Import failed")}])
                              (rf/dispatch [:import-loading-done]))})
   {:db (assoc-in db [:import :loading] true)}))

(rf/reg-event-db
 :import-loading-done
 (fn [db _]
   (assoc-in db [:import :loading] false)))

(rf/reg-event-db
 :import-complete
 (fn [db [_ result]]
   (rf/dispatch [:set-notification {:type :success
                                    :message (str "Imported " (:imported result) " products")}])
   (rf/dispatch [:fetch-products])
   (-> db
       (assoc-in [:import :loading] false)
       (assoc-in [:import :result] result))))

(rf/reg-event-db
 :add-to-cart
 (fn [db [_ product quantity]]
   (let [items  (get-in db [:cart :items])
         exists (some #(when (= (:id %) (:id product)) %) items)]
     (if exists
       (assoc-in db [:cart :items]
                 (mapv (fn [item]
                         (if (= (:id item) (:id product))
                           (update item :quantity + quantity)
                           item))
                       items))
       (update-in db [:cart :items] conj
                  (assoc product :quantity quantity))))))

(rf/reg-event-db
 :remove-from-cart
 (fn [db [_ product-id]]
   (update-in db [:cart :items]
              (fn [items] (filterv #(not= (:id %) product-id) items)))))

(rf/reg-event-db
 :update-cart-quantity
 (fn [db [_ product-id quantity]]
   (if (<= quantity 0)
     (update-in db [:cart :items]
                (fn [items] (filterv #(not= (:id %) product-id) items)))
     (assoc-in db [:cart :items]
               (mapv (fn [item]
                       (if (= (:id item) product-id)
                         (assoc item :quantity quantity)
                         item))
                     (get-in db [:cart :items]))))))

(rf/reg-event-db
 :clear-cart
 (fn [db _]
   (assoc-in db [:cart :items] [])))

(rf/reg-event-fx
 :place-order
 (fn [{:keys [db]} _]
   (let [items (get-in db [:cart :items])
         order {:items (mapv (fn [item]
                               {:product-id (:id item)
                                :quantity   (:quantity item)})
                             items)}]
     (http/post! {:uri        "/api/orders"
                  :body       order
                  :on-success (fn [_]
                                (rf/dispatch [:clear-cart])
                                (rf/dispatch [:set-notification {:type :success :message "Order placed successfully!"}])
                                (rf/dispatch [:fetch-orders])
                                (set! (.-hash js/location) "#/orders"))
                  :on-failure #(rf/dispatch [:set-notification {:type :error :message (or (:error %) "Order failed")}])})
     {})))

(rf/reg-event-fx
 :fetch-orders
 (fn [{:keys [db]} _]
   (http/get! {:uri        "/api/orders"
               :on-success #(rf/dispatch [:orders-loaded %])
               :on-failure #(rf/dispatch [:set-notification {:type :error :message "Failed to load orders"}])})
   {:db (assoc-in db [:orders :loading] true)}))

(rf/reg-event-db
 :orders-loaded
 (fn [db [_ orders]]
   (-> db
       (assoc-in [:orders :items] orders)
       (assoc-in [:orders :loading] false))))

(rf/reg-event-fx
 :fetch-order-detail
 (fn [{:keys [db]} [_ order-id]]
   (http/get! {:uri        (str "/api/orders/" order-id)
               :on-success #(rf/dispatch [:order-detail-loaded order-id %])
               :on-failure #(rf/dispatch [:set-notification {:type :error :message "Failed to load order details"}])})
   {}))

(rf/reg-event-db
 :order-detail-loaded
 (fn [db [_ order-id detail]]
   (assoc-in db [:orders :details order-id] detail)))

(rf/reg-event-db
 :set-search-query
 (fn [db [_ query]]
   (assoc-in db [:search :query] query)))

(rf/reg-event-db
 :set-search-category
 (fn [db [_ category]]
   (assoc-in db [:search :category] category)))

(rf/reg-event-fx
 :login
 (fn [{:keys [db]} [_ {:keys [username password]}]]
   (http/post! {:uri        "/api/auth/login"
                :body       {:username username :password password}
                :on-success (fn [response]
                              (rf/dispatch [:login-success response]))
                :on-failure (fn [err]
                              (rf/dispatch [:login-failure (or (:error err) "Login failed")]))})
   {:db (-> db
            (assoc-in [:auth :loading] true)
            (assoc-in [:auth :error] nil))}))

(rf/reg-event-fx
 :login-success
 (fn [{:keys [db]} [_ {:keys [token username role email]}]]
   (set! (.-hash js/location) "#/")
   {:db (-> db
            (assoc-in [:auth :token] token)
            (assoc-in [:auth :user] {:username username :role role :email email})
            (assoc-in [:auth :loading] false)
            (assoc-in [:auth :error] nil))
    :fx [[:dispatch [:set-notification {:type :success :message (str "Welcome, " username "! (role: " role ")")}]]]}))

(rf/reg-event-fx
 :login-failure
 (fn [{:keys [db]} [_ error-msg]]
   {:db (-> db
            (assoc-in [:auth :loading] false)
            (assoc-in [:auth :error] error-msg))}))

(rf/reg-event-fx
 :logout
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:auth :token] nil)
            (assoc-in [:auth :user] nil)
            (assoc-in [:cart :items] []))
    :fx [[:dispatch [:set-notification {:type :success :message "Logged out"}]]]}))
