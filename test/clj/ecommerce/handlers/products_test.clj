(ns ecommerce.handlers.products-test
  (:require [clojure.test :refer [deftest testing is]]
            [ecommerce.handlers.products :as h]
            [clojure.java.io :as io]))

(deftest test-list-products
  (testing "returns 200 with products"
    (with-redefs [ecommerce.services.product-service/list-products
                  (constantly {:items [{:id "1"}] :total 1 :page 1 :per-page 20})]
      (let [response (h/list-products {:params {}})]
        (is (= 200 (:status response)))
        (is (= 1 (get-in response [:body :total])))))))

(deftest test-get-product-not-found
  (testing "returns 404 when service returns nil"
    (with-redefs [ecommerce.services.product-service/get-product (constantly nil)]
      (let [response (h/get-product {:path-params {:id "non-existent"}})]
        (is (= 404 (:status response)))
        (is (= "Product not found" (get-in response [:body :error])))))))

(deftest test-get-product-found
  (testing "returns 200 with product"
    (let [product {:id "123" :name "Test" :sku "T-1"}]
      (with-redefs [ecommerce.services.product-service/get-product (constantly product)]
        (let [response (h/get-product {:path-params {:id "123"}})]
          (is (= 200 (:status response)))
          (is (= product (:body response))))))))

(deftest test-create-product-success
  (testing "returns 201 on success"
    (let [product {:id "123" :name "Test" :sku "T-1"}]
      (with-redefs [ecommerce.services.product-service/create-product (constantly product)]
        (let [response (h/create-product {:body-params {:name "Test" :sku "T-1"}})]
          (is (= 201 (:status response))))))))

(deftest test-create-product-error
  (testing "returns 400 on error"
    (with-redefs [ecommerce.services.product-service/create-product
                  (constantly {:error "duplicate sku"})]
      (let [response (h/create-product {:body-params {:name "Test" :sku "T-1"}})]
        (is (= 400 (:status response)))))))

(deftest test-update-product-success
  (testing "returns 200 on success"
    (with-redefs [ecommerce.services.product-service/update-product
                  (constantly {:id "123" :name "Updated"})]
      (let [response (h/update-product {:path-params {:id "123"} :body-params {:name "Updated"}})]
        (is (= 200 (:status response)))))))

(deftest test-update-product-not-found
  (testing "returns 404 when product not found"
    (with-redefs [ecommerce.services.product-service/update-product
                  (constantly {:error "Product not found"})]
      (let [response (h/update-product {:path-params {:id "123"} :body-params {}})]
        (is (= 404 (:status response)))))))

(deftest test-update-product-validation-error
  (testing "returns 400 on validation error"
    (with-redefs [ecommerce.services.product-service/update-product
                  (constantly {:error "Invalid price"})]
      (let [response (h/update-product {:path-params {:id "123"} :body-params {:price "-1"}})]
        (is (= 400 (:status response)))))))

(deftest test-delete-product-success
  (testing "returns 204 on success"
    (with-redefs [ecommerce.services.product-service/delete-product
                  (constantly {:deleted true})]
      (let [response (h/delete-product {:path-params {:id "123"}})]
        (is (= 204 (:status response)))))))

(deftest test-delete-product-not-found
  (testing "returns 404 when not found"
    (with-redefs [ecommerce.services.product-service/delete-product
                  (constantly {:error "Product not found"})]
      (let [response (h/delete-product {:path-params {:id "123"}})]
        (is (= 404 (:status response)))))))

(deftest test-delete-all-products
  (testing "returns 200 with delete count"
    (with-redefs [ecommerce.services.product-service/delete-all-products
                  (constantly {:deleted 10})]
      (let [response (h/delete-all-products {})]
        (is (= 200 (:status response)))
        (is (= 10 (get-in response [:body :deleted])))))))

(deftest test-text-file?
  (testing "recognizes text files"
    (let [f (java.io.File/createTempFile "test" ".csv")]
      (spit f "name,sku,price\nWidget,W-1,10")
      (is (true? (#'h/text-file? f)))))
  (testing "recognizes UTF-8 text files"
    (let [f (java.io.File/createTempFile "test" ".csv")]
      (spit f "name,sku,price\nCafé Latte,CL-1,4.50\nJalapeño,JP-1,2.00")
      (is (true? (#'h/text-file? f)))))
  (testing "rejects binary files with NUL bytes"
    (let [f (java.io.File/createTempFile "test" ".csv")]
      (with-open [os (io/output-stream f)]
        (.write os (byte-array [0x00 0x01 0x02 0x03])))
      (is (not (#'h/text-file? f)))))
  (testing "rejects binary files with control characters"
    (let [f (java.io.File/createTempFile "test" ".csv")]
      (with-open [os (io/output-stream f)]
        (.write os (byte-array (map unchecked-byte [0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A]))))
      (is (not (#'h/text-file? f))))))

(deftest test-import-csv-no-file
  (testing "returns 400 when no file uploaded"
    (let [response (h/import-csv {:multipart-params {}})]
      (is (= 400 (:status response)))
      (is (= "No file uploaded" (get-in response [:body :error])))))
  (testing "returns 400 when file is nil"
    (let [response (h/import-csv {:multipart-params {"file" nil}})]
      (is (= 400 (:status response))))))

(deftest test-import-csv-wrong-extension
  (testing "rejects non-csv files"
    (let [f (java.io.File/createTempFile "test" ".txt")]
      (spit f "data")
      (let [response (h/import-csv {:multipart-params {"file" {:filename "test.txt" :tempfile f}}})]
        (is (= 400 (:status response)))
        (is (re-find #"Only .csv" (get-in response [:body :error]))))))
  (testing "rejects when filename is nil"
    (let [f (java.io.File/createTempFile "test" ".csv")]
      (spit f "data")
      (let [response (h/import-csv {:multipart-params {"file" {:filename nil :tempfile f}}})]
        (is (= 400 (:status response)))))))

(deftest test-import-csv-too-large
  (testing "rejects files over 20MB"
    (let [f (java.io.File/createTempFile "test" ".csv")]
      (spit f "data")
      (let [response (h/import-csv {:multipart-params {"file" {:filename "big.csv"
                                                               :tempfile (proxy [java.io.File] [(.getPath f)]
                                                                           (length [] (* 21 1024 1024)))}}})]
        (is (= 400 (:status response)))
        (is (re-find #"too large" (get-in response [:body :error])))))))

(deftest test-import-csv-binary-content
  (testing "rejects binary content disguised as csv (PNG header)"
    (let [f (java.io.File/createTempFile "test" ".csv")]
      (with-open [os (io/output-stream f)]
        (.write os (byte-array (map unchecked-byte [0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A]))))
      (let [response (h/import-csv {:multipart-params {"file" {:filename "data.csv" :tempfile f}}})]
        (is (= 400 (:status response)))
        (is (re-find #"binary" (get-in response [:body :error])))))))

(deftest test-import-csv-success
  (testing "processes valid csv file"
    (let [f (java.io.File/createTempFile "test" ".csv")]
      (spit f "name,sku,price\nWidget,W-1,10")
      (with-redefs [ecommerce.services.product-service/import-csv
                    (constantly {:imported 1 :errors []})]
        (let [response (h/import-csv {:multipart-params {"file" {:filename "products.csv" :tempfile f}}})]
          (is (= 200 (:status response)))
          (is (= 1 (get-in response [:body :imported]))))))))
