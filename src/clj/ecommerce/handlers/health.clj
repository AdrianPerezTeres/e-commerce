(ns ecommerce.handlers.health)

(defn check [_request]
  {:status 200
   :body   {:status "ok"
            :time   (str (java.time.Instant/now))}})
