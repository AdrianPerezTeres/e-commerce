(ns ecommerce.middleware.auth
  (:require [clojure.string :as str]
            [buddy.sign.jwt :as jwt]
            [ecommerce.config :refer [config]]))

(defn- jwt-secret []
  (get-in config [:auth :jwt-secret]))

(defn- verify-token [token]
  (try
    (jwt/unsign token (jwt-secret) {:alg :hs256})
    (catch Exception _
      nil)))

(defn- extract-user [claims]
  (when claims
    {:user-id  (:sub claims)
     :email    (or (:email claims) (:username claims) (:sub claims))
     :username (or (:username claims) (:sub claims))
     :role     (or (:role claims) "reader")}))

(defn wrap-auth
  [handler]
  (fn [request]
    (let [auth-header (get-in request [:headers "authorization"])
          token       (when auth-header
                        (second (re-find #"(?i)Bearer\s+(.+)" auth-header)))
          claims      (when token (verify-token token))
          user        (extract-user claims)]
      (handler (assoc request :identity user)))))

(defn- auth-required? []
  (get-in config [:auth :required]))

(defn wrap-require-auth
  [handler]
  (fn [request]
    (if (:identity request)
      (handler request)
      (if (not (auth-required?))
        (handler (assoc request :identity {:user-id  "admin"
                                           :username "admin"
                                           :email    "admin@demo.local"
                                           :role     "admin"}))
        {:status 401
         :body   {:error "Authentication required. Please log in."}}))))

(defn wrap-require-role
  [handler & allowed-roles]
  (let [roles (set allowed-roles)]
    (fn [request]
      (let [user-role (get-in request [:identity :role])]
        (if (contains? roles user-role)
          (handler request)
          {:status 403
           :body   {:error (str "Access denied. Required role: " (str/join " or " allowed-roles))}})))))
