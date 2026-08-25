(ns ecommerce.middleware.auth-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.middleware.auth :as auth]))

(deftest test-extract-user
  (testing "extracts user from claims"
    (let [claims {:sub "user-123" :email "test@example.com" (keyword "cognito:username") "testuser"}
          user   (#'auth/extract-user claims)]
      (is (= "user-123" (:user-id user)))
      (is (= "test@example.com" (:email user)))
      (is (= "testuser" (:username user)))))
  (testing "returns nil for nil claims"
    (is (nil? (#'auth/extract-user nil)))))

(deftest test-wrap-require-auth
  (testing "allows authenticated requests"
    (let [handler (auth/wrap-require-auth (fn [req] {:status 200 :body "ok"}))
          response (handler {:identity {:user-id "123"}})]
      (is (= 200 (:status response)))))
  (testing "rejects unauthenticated requests"
    (let [orig @#'auth/auth-required?]
      (try
        (alter-var-root #'auth/auth-required? (constantly (constantly true)))
        (let [handler (auth/wrap-require-auth (fn [req] {:status 200 :body "ok"}))
              response (handler {:identity nil})]
          (is (= 401 (:status response))))
        (finally
          (alter-var-root #'auth/auth-required? (constantly orig)))))))

(deftest test-wrap-auth-no-token
  (testing "sets identity to nil when no auth header"
    (let [captured (atom nil)
          handler (auth/wrap-auth (fn [req] (reset! captured (:identity req)) {:status 200}))
          _ (handler {:headers {}})]
      (is (nil? @captured)))))
