(ns ecommerce.middleware.error
  (:require [clojure.tools.logging :as log]))

(defn wrap-exception [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error e "Unhandled exception" {:uri (:uri request) :method (:request-method request)})
        {:status 500
         :body   {:error "Internal server error"}}))))
