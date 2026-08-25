(ns ecommerce.middleware.auth
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as keys]
            [clojure.tools.logging :as log]
            [ecommerce.config :refer [config]])
  (:import [java.util Base64]))

(defonce jwks-cache (atom nil))

(defn- fetch-jwks []
  (let [{:keys [region user-pool-id]} (:cognito config)
        url (str "https://cognito-idp." region ".amazonaws.com/"
                 user-pool-id "/.well-known/jwks.json")]
    (try
      (let [response (http/get url {:as :json})]
        (reset! jwks-cache (:keys (:body response)))
        @jwks-cache)
      (catch Exception e
        (log/error e "Failed to fetch JWKS")
        nil))))

(defn- get-jwks []
  (or @jwks-cache (fetch-jwks)))

(defn- find-key [kid]
  (when-let [jwks (get-jwks)]
    (->> jwks
         (filter #(= (:kid %) kid))
         first)))

(defn- decode-jwt-header [token]
  (try
    (let [header-part (first (str/split token #"\."))
          decoded     (.decode (Base64/getUrlDecoder) header-part)]
      (json/read-str (String. decoded) :key-fn keyword))
    (catch Exception _ nil)))

(defn- verify-cognito-token [token]
  (try
    (let [header  (decode-jwt-header token)
          jwk     (find-key (:kid header))
          pub-key (keys/jwk->public-key jwk)]
      (jwt/unsign token pub-key {:alg :rs256}))
    (catch Exception e
      (log/warn "Cognito JWT verification failed:" (.getMessage e))
      nil)))

(def ^:private jwt-secret "ecommerce-demo-secret-key-2024")

(defn- verify-local-token [token]
  (try
    (jwt/unsign token jwt-secret {:alg :hs256})
    (catch Exception _
      nil)))

(defn- cognito-configured? []
  (let [pool-id (get-in config [:cognito :user-pool-id])]
    (and pool-id (not (str/blank? pool-id)))))

(defn- extract-user [claims]
  (when claims
    {:user-id  (:sub claims)
     :email    (or (:email claims) (:username claims) (:sub claims))
     :username (or (get claims (keyword "cognito:username")) (:username claims) (:sub claims))
     :role     (or (:role claims)
                   (get-in claims [:custom:role])
                   "reader")}))

(defn- verify-token [token]
  (if (cognito-configured?)
    (or (verify-cognito-token token) (verify-local-token token))
    (verify-local-token token)))

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
