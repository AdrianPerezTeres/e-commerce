(ns ecommerce.middleware.auth-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.middleware.auth :as auth]
            [ecommerce.config :refer [config]]
            [buddy.sign.jwt :as jwt]))

(def test-secret "test-secret-key-for-unit-tests")

(defn- make-token [claims]
  (jwt/sign claims test-secret {:alg :hs256}))

(deftest test-extract-user
  (testing "extracts user from claims"
    (let [claims {:sub "user-123" :email "test@example.com" :username "testuser" :role "admin"}
          user   (#'auth/extract-user claims)]
      (is (= "user-123" (:user-id user)))
      (is (= "test@example.com" (:email user)))
      (is (= "testuser" (:username user)))
      (is (= "admin" (:role user)))))
  (testing "defaults role to reader when missing"
    (let [user (#'auth/extract-user {:sub "u1"})]
      (is (= "reader" (:role user)))))
  (testing "falls back email to username then sub"
    (let [user (#'auth/extract-user {:sub "u1" :username "testuser"})]
      (is (= "testuser" (:email user))))
    (let [user (#'auth/extract-user {:sub "u1"})]
      (is (= "u1" (:email user)))))
  (testing "returns nil for nil claims"
    (is (nil? (#'auth/extract-user nil)))))

(deftest test-verify-token-valid
  (testing "valid token returns claims"
    (with-redefs [config {:auth {:jwt-secret test-secret}}]
      (let [token  (make-token {:sub "user1" :role "admin"})
            claims (#'auth/verify-token token)]
        (is (= "user1" (:sub claims)))
        (is (= "admin" (:role claims)))))))

(deftest test-verify-token-invalid
  (testing "invalid token returns nil"
    (with-redefs [config {:auth {:jwt-secret test-secret}}]
      (is (nil? (#'auth/verify-token "invalid-token"))))))

(deftest test-verify-token-wrong-secret
  (testing "token signed with different secret returns nil"
    (with-redefs [config {:auth {:jwt-secret "different-secret"}}]
      (let [token (jwt/sign {:sub "user1"} "other-secret" {:alg :hs256})]
        (is (nil? (#'auth/verify-token token)))))))

(deftest test-wrap-auth-with-valid-token
  (testing "sets identity from valid Bearer token"
    (with-redefs [config {:auth {:jwt-secret test-secret}}]
      (let [token    (make-token {:sub "user1" :username "testuser" :email "test@ex.com" :role "buyer"})
            captured (atom nil)
            handler  (auth/wrap-auth (fn [req] (reset! captured (:identity req)) {:status 200}))
            _        (handler {:headers {"authorization" (str "Bearer " token)}})]
        (is (= "user1" (:user-id @captured)))
        (is (= "testuser" (:username @captured)))
        (is (= "buyer" (:role @captured)))))))

(deftest test-wrap-auth-no-token
  (testing "sets identity to nil when no auth header"
    (let [captured (atom nil)
          handler (auth/wrap-auth (fn [req] (reset! captured (:identity req)) {:status 200}))
          _ (handler {:headers {}})]
      (is (nil? @captured)))))

(deftest test-wrap-auth-invalid-token
  (testing "sets identity to nil for invalid token"
    (with-redefs [config {:auth {:jwt-secret test-secret}}]
      (let [captured (atom nil)
            handler  (auth/wrap-auth (fn [req] (reset! captured (:identity req)) {:status 200}))
            _        (handler {:headers {"authorization" "Bearer bad-token"}})]
        (is (nil? @captured))))))

(deftest test-wrap-auth-case-insensitive
  (testing "handles lowercase bearer prefix"
    (with-redefs [config {:auth {:jwt-secret test-secret}}]
      (let [token    (make-token {:sub "u1" :role "admin"})
            captured (atom nil)
            handler  (auth/wrap-auth (fn [req] (reset! captured (:identity req)) {:status 200}))
            _        (handler {:headers {"authorization" (str "bearer " token)}})]
        (is (some? @captured))))))

(deftest test-wrap-require-auth-authenticated
  (testing "allows authenticated requests"
    (let [handler  (auth/wrap-require-auth (fn [_] {:status 200 :body "ok"}))
          response (handler {:identity {:user-id "123"}})]
      (is (= 200 (:status response))))))

(deftest test-wrap-require-auth-fallback
  (testing "falls back to admin when auth not required"
    (with-redefs [config {:auth {:required false}}]
      (let [captured (atom nil)
            handler  (auth/wrap-require-auth (fn [req] (reset! captured (:identity req)) {:status 200}))
            _        (handler {:identity nil})]
        (is (= "admin" (:username @captured)))
        (is (= "admin" (:role @captured)))))))

(deftest test-wrap-require-auth-rejects
  (testing "rejects when auth required and no identity"
    (with-redefs [config {:auth {:required true}}]
      (let [handler  (auth/wrap-require-auth (fn [_] {:status 200}))
            response (handler {:identity nil})]
        (is (= 401 (:status response)))
        (is (re-find #"Authentication required" (get-in response [:body :error])))))))

(deftest test-wrap-require-role-allowed
  (testing "allows matching role"
    (let [handler  (auth/wrap-require-role (fn [_] {:status 200}) "admin" "buyer")
          response (handler {:identity {:role "admin"}})]
      (is (= 200 (:status response))))))

(deftest test-wrap-require-role-buyer
  (testing "allows buyer role"
    (let [handler  (auth/wrap-require-role (fn [_] {:status 200}) "admin" "buyer")
          response (handler {:identity {:role "buyer"}})]
      (is (= 200 (:status response))))))

(deftest test-wrap-require-role-denied
  (testing "denies non-matching role"
    (let [handler  (auth/wrap-require-role (fn [_] {:status 200}) "admin")
          response (handler {:identity {:role "reader"}})]
      (is (= 403 (:status response)))
      (is (re-find #"Access denied" (get-in response [:body :error]))))))
