(ns ecommerce.http
  (:require [re-frame.core :as rf]
            [re-frame.db :as rfdb]))

(def api-base "")

(defn- request [{:keys [method uri body on-success on-failure multipart?]}]
  (let [token (get-in @rfdb/app-db [:auth :token])
        headers (cond-> {"Accept" "application/json"}
                  (not multipart?)
                  (assoc "Content-Type" "application/json")
                  token
                  (assoc "Authorization" (str "Bearer " token)))
        opts (cond-> {:method  method
                      :headers headers}
               (and body (not multipart?))
               (assoc :body (js/JSON.stringify (clj->js body)))
               multipart?
               (assoc :body body))]
    (-> (js/fetch (str api-base uri) (clj->js opts))
        (.then (fn [response]
                 (if (.-ok response)
                   (if (= 204 (.-status response))
                     (on-success nil)
                     (.then (.json response)
                            (fn [data] (on-success (js->clj data :keywordize-keys true)))))
                   (if (= 401 (.-status response))
                     (rf/dispatch [:login {:username "admin" :password "admin123"}])
                     (.then (.json response)
                            (fn [data]
                              (when on-failure
                                (on-failure (js->clj data :keywordize-keys true)))))))))
        (.catch (fn [err]
                  (when on-failure
                    (on-failure {:error (.-message err)})))))))

(defn get! [opts] (request (assoc opts :method "GET")))
(defn post! [opts] (request (assoc opts :method "POST")))
(defn put! [opts] (request (assoc opts :method "PUT")))
(defn delete! [opts] (request (assoc opts :method "DELETE")))
