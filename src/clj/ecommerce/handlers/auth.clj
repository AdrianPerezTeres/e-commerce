(ns ecommerce.handlers.auth
  (:require [buddy.sign.jwt :as jwt]
            [clojure.tools.logging :as log]
            [ecommerce.config :refer [config]]))

(defn- jwt-secret []
  (get-in config [:auth :jwt-secret]))

(def ^:private demo-users
  {"admin"  {:password "admin123"  :role "admin"  :email "admin@demo.local"}
   "buyer"  {:password "buyer123"  :role "buyer"  :email "buyer@demo.local"}
   "reader" {:password "reader123" :role "reader" :email "reader@demo.local"}})

(defn login [request]
  (let [{:keys [username password]} (:body-params request)
        user (get demo-users username)]
    (cond
      (not user)
      {:status 401 :body {:error "Invalid username or password"}}

      (not= password (:password user))
      {:status 401 :body {:error "Invalid username or password"}}

      :else
      (let [claims {:sub      username
                    :username username
                    :email    (:email user)
                    :role     (:role user)
                    :exp      (+ (quot (System/currentTimeMillis) 1000) 86400)}
            token  (jwt/sign claims (jwt-secret) {:alg :hs256})]
        (log/info "User logged in:" username "role:" (:role user))
        {:status 200
         :body   {:token    token
                  :username username
                  :role     (:role user)
                  :email    (:email user)}}))))

(defn me [request]
  (let [identity (:identity request)]
    (if identity
      {:status 200
       :body   (select-keys identity [:username :email :role :user-id])}
      {:status 401
       :body   {:error "Not authenticated"}})))

