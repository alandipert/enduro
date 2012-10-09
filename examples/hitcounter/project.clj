(defproject alandipert/enduro-example-hitcounter "0.0.1-SNAPSHOT"
  :description "An example Heroku application using enduro for persistence."
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [ring/ring-core "1.1.6"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [compojure "1.1.3"]
                 [alandipert/enduro "1.1.0"]])
