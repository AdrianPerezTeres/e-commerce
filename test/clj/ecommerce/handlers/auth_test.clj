(ns ecommerce.handlers.auth-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.handlers.auth :as auth]
            [ecommerce.config :refer [config]]))

(deftest test-login-success
  (testing "valid admin login returns token"
    (with-redefs [config {:auth {:jwt-secret "test-secret"}}]
      (let [response (auth/login {:body-params {:username "admin" :password "admin123"}})]
        (is (= 200 (:status response)))
        (is (string? (get-in response [:body :token])))
        (is (= "admin" (get-in response [:body :username])))
        (is (= "admin" (get-in response [:body :role])))
        (is (= "admin@demo.local" (get-in response [:body :email])))))))

(deftest test-login-buyer
  (testing "valid buyer login"
    (with-redefs [config {:auth {:jwt-secret "test-secret"}}]
      (let [response (auth/login {:body-params {:username "buyer" :password "buyer123"}})]
        (is (= 200 (:status response)))
        (is (= "buyer" (get-in response [:body :role])))))))

(deftest test-login-reader
  (testing "valid reader login"
    (with-redefs [config {:auth {:jwt-secret "test-secret"}}]
      (let [response (auth/login {:body-params {:username "reader" :password "reader123"}})]
        (is (= 200 (:status response)))
        (is (= "reader" (get-in response [:body :role])))))))

(deftest test-login-invalid-username
  (testing "unknown username returns 401"
    (let [response (auth/login {:body-params {:username "hacker" :password "pass"}})]
      (is (= 401 (:status response)))
      (is (= "Invalid username or password" (get-in response [:body :error]))))))

(deftest test-login-wrong-password
  (testing "wrong password returns 401"
    (let [response (auth/login {:body-params {:username "admin" :password "wrong"}})]
      (is (= 401 (:status response)))
      (is (= "Invalid username or password" (get-in response [:body :error]))))))

(deftest test-me-authenticated
  (testing "returns user info when identity present"
    (let [identity {:username "admin" :email "admin@demo.local" :role "admin" :user-id "u1"}
          response (auth/me {:identity identity})]
      (is (= 200 (:status response)))
      (is (= "admin" (get-in response [:body :username])))
      (is (= "admin" (get-in response [:body :role]))))))

(deftest test-me-unauthenticated
  (testing "returns 401 when no identity"
    (let [response (auth/me {:identity nil})]
      (is (= 401 (:status response)))
      (is (= "Not authenticated" (get-in response [:body :error]))))))
