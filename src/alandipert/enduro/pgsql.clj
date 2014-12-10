(ns alandipert.enduro.pgsql
  (:require [alandipert.enduro :as e]
            [clojure.java.jdbc :as sql]))

(defn create-enduro-table! [table-name]
  (sql/create-table table-name [:id :int] [:value :text]))

(defn delete-enduro-table! [table-name]
  (sql/drop-table table-name))

(defn table-exists? [table-name]
  (sql/with-query-results
    tables ["SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"]
    (get (into #{} (map :table_name tables)) table-name)))

(defn get-value [table-name]
  (sql/with-query-results rows
    [(str "SELECT value FROM " table-name " LIMIT 1")]
    (read-string (:value (first rows)))))

(deftype PostgreSQLBackend [db-config table-name]
  e/IDurableBackend
  (-commit!
    [this value]
    (sql/with-connection db-config
      (sql/transaction
       (sql/update-or-insert-values
        table-name ["id=?" 0] {:id 0 :value (pr-str value)}))))
  (-remove! [this]
    (sql/with-connection db-config
      (delete-enduro-table! table-name))))

(defn postgresql-atom
  #=(e/with-options-doc "Creates and returns a PostgreSQL-backed atom. If the location
  denoted by the combination of db-config and table-name exists, it is
  read and becomes the initial value. Otherwise, the initial value is
  init and the table denoted by table-name is updated.

  db-config can be a String URI, a map
  of :username/:password/:host/:port, or any other type of
  configuration object understood by
  clojure.java.jdbc/with-connection")
  [init db-config table-name & opts]
  (e/atom*
   (sql/with-connection db-config
     (or (and (table-exists? table-name)
              (get-value table-name))
         (do
           (create-enduro-table! table-name)
           init)))
   (PostgreSQLBackend. db-config table-name)
   (apply hash-map opts)))
