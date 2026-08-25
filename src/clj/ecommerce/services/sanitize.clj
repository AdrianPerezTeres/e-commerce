(ns ecommerce.services.sanitize
  "Shared input sanitization — applied to both CSV import and CRUD operations.
   Centralizes XSS, formula injection, and SQL injection defenses."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]))

(def html-pattern #"<[^>]*>")
(def sqli-pattern #"(?i)('.*(--)|(;\s*(DROP|DELETE|UPDATE|INSERT|ALTER|EXEC|UNION)))")
(def formula-pattern #"^[=+\-@\|]")

(defn strip-html [s]
  (when s
    (str/replace s html-pattern "")))

(defn strip-formula-prefix [s]
  (when s
    (str/replace s formula-pattern "")))

(defn sanitize-text
  "Strips HTML tags and formula prefixes from a text field."
  [s]
  (-> s strip-html strip-formula-prefix))

(defn detect-threats
  "Scans a map of field-name→value for XSS, SQL injection, and formula injection patterns.
   Returns a vector of threat maps. Used for logging/reporting."
  [fields-map source]
  (let [threats (reduce-kv
                 (fn [threats field value]
                   (if (or (nil? value) (str/blank? value))
                     threats
                     (cond-> threats
                       (re-find html-pattern value)
                       (conj {:field field :type "XSS" :detail "HTML/script tags detected and stripped"})
                       (re-find sqli-pattern value)
                       (conj {:field field :type "SQL Injection" :detail "SQL injection pattern detected"})
                       (re-find formula-pattern value)
                       (conj {:field field :type "Formula Injection" :detail "Formula prefix detected and stripped"}))))
                 []
                 fields-map)]
    (when (seq threats)
      (let [summary (->> threats
                         (group-by :type)
                         (map (fn [[t v]] (str t ": " (count v))))
                         (str/join ", "))]
        (log/warn (str "Threats detected in " source " — [" summary "]"))))
    threats))

(defn has-malicious-content?
  "Returns true if threats include XSS or SQL Injection (not just formula injection)."
  [threats]
  (some #(#{"XSS" "SQL Injection"} (:type %)) threats))

(defn sanitize-product
  "Sanitizes product text fields (name, description). Detects and logs threats.
   Returns {:data sanitized-map :threats [...]} so callers can reject malicious input."
  [data source]
  (let [threats (detect-threats {"name"        (:name data)
                                 "description" (:description data)}
                                source)
        clean   (-> data
                    (update :name sanitize-text)
                    (update :description sanitize-text))]
    {:data clean :threats threats}))
