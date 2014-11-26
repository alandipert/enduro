(defproject alandipert/enduro "1.1.5"
  :description "Durable Atoms for Clojure"
  :url "https://github.com/alandipert/enduro"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [postgresql "8.4-702.jdbc4"]
                 [org.clojure/java.jdbc "0.2.3"]]
  
  :test-selectors {:default (fn [m] (not (:postgres m)))
                   :all     (constantly true)}
  
  :jvm-opts ["-Dtest.database.url=postgresql://localhost:5432/enduro_test"])
