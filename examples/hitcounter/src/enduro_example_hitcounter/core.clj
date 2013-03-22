(ns enduro-example-hitcounter.core
  (:require [compojure.core :refer [defroutes GET]]
            [ring.adapter.jetty :refer [run-jetty]]
            [alandipert.enduro.pgsql :as pg]
            [alandipert.enduro :as enduro]))

(def pgsql
  (delay
   (pg/postgresql-atom 0 (System/getenv "DATABASE_URL") "enduro")))

(defn handler [req]
  (str "Hits: " (enduro/swap! @pgsql inc)))

(defroutes routes
  (GET "/" req handler))

(defn -main [port]
  (run-jetty routes {:port (Integer. port)}))
