(ns ecommerce.routes-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.routes :as routes]
            [ecommerce.config :refer [config]]
            [mount.core :as mount]))

(deftest test-api-routes-structure
  (testing "api-routes is a vector starting with /api"
    (is (vector? routes/api-routes))
    (is (= "/api" (first routes/api-routes)))))

(deftest test-create-app
  (testing "create-app returns a function (Ring handler)"
    (mount/start #'ecommerce.config/config)
    (try
      (let [app (routes/create-app)]
        (is (fn? app)))
      (finally
        (mount/stop #'ecommerce.config/config)))))

(deftest test-health-endpoint
  (testing "GET /api/health returns 200"
    (mount/start #'ecommerce.config/config)
    (try
      (let [app      (routes/create-app)
            response (app {:request-method :get
                           :uri            "/api/health"
                           :headers        {}})]
        (is (= 200 (:status response))))
      (finally
        (mount/stop #'ecommerce.config/config)))))

(deftest test-not-found-returns-index
  (testing "unknown route returns index.html"
    (mount/start #'ecommerce.config/config)
    (try
      (let [app      (routes/create-app)
            response (app {:request-method :get
                           :uri            "/unknown-page"
                           :headers        {}})]
        (is (= 200 (:status response)))
        (is (= "text/html" (get-in response [:headers "Content-Type"]))))
      (finally
        (mount/stop #'ecommerce.config/config)))))

(deftest test-login-endpoint
  (testing "POST /api/auth/login returns 200 for valid creds"
    (mount/start #'ecommerce.config/config)
    (try
      (let [app      (routes/create-app)
            response (app {:request-method :post
                           :uri            "/api/auth/login"
                           :headers        {"content-type" "application/json"}
                           :body           (java.io.ByteArrayInputStream.
                                            (.getBytes "{\"username\":\"admin\",\"password\":\"admin123\"}"))})]
        (is (= 200 (:status response))))
      (finally
        (mount/stop #'ecommerce.config/config)))))
