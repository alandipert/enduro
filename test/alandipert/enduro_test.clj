(ns alandipert.enduro-test
  (:use clojure.test)
  (:require [alandipert.enduro :as e]
            [clojure.java.io :as io]
            [alandipert.enduro.pgsql :as pg]
            [clojure.java.jdbc :as sql])
  (:import java.io.File
           java.util.concurrent.CountDownLatch))

;;; core and file-atom tests

(defn tmp []
  (File/createTempFile "enduro" ".clj"))

(def test-db-property "test.database.url")

(def db-connection-err (str "This test requires that the " test-db-property " system property be a valid JDBC connection string. For example: 'postgresql://localhost:5432/your-db'"))

(defn get-connection []
  (if-let [database-url (System/getProperty test-db-property)]
    (try (sql/with-connection database-url
           database-url) 
         (catch org.postgresql.util.PSQLException p
           (throw (RuntimeException. db-connection-err p))))
    (throw (RuntimeException. db-connection-err))))

(defn some-random-table-name [] (str "test_enduro_" (rand-int (Integer/MAX_VALUE))))

(defn pull-out-next-exception [outer]
  (let [next  (.getNextException outer)
        cause (.getCause outer)]
    (cond
     next (do (println (.getMessage outer)) (recur next))
     cause (do (println (.getMessage outer)) (recur cause))
     :else outer)))

(defn call-with-sql-exception-unwrapping
  "If JDBC gives us a BatchUpdateException, then the
  real error message is deeper in and not displayed.
  Drag it out of there."
  [f]
  (try
    (f)
    (catch java.sql.SQLException s
      (throw (pull-out-next-exception s)))))

(defmacro with-sql-exception-unwrapping
  [& body]
  `(call-with-sql-exception-unwrapping (fn [] ~@body)))

(deftest initial-value
  (testing "initialization of file"
    (time
     (let [file (tmp)
           ea (e/file-atom ["testing" 1 2 3] file :pending-dir "/tmp")]
       (is (= ["testing" 1 2 3] (e/read-file file)))))))

(deftest write-file-atom
  (testing "concurrency of file atom"
    (time
     (let [file (tmp)
           ea (e/file-atom 0 file :pending-dir "/tmp")
           n 100
           latch (CountDownLatch. n)]

       (add-watch ea :x (fn [_ _ _ x] (.countDown latch)))

       (dotimes [_ n]
         (future
           (e/swap! ea inc)))

       (.await latch)

       (is (= n (e/read-file file)))
       
       (testing "after release, attempting to read the atom should cause an exception"
         (e/release! ea)
         (is (thrown? AssertionError @ea))
         (is (not (.exists file))))))))

(deftest write-mem-atom
  (testing "concurrency of mem atom"
    (time
     (let [ea (e/mem-atom 0)
           n 100
           latch (CountDownLatch. n)]

       (add-watch ea :x (fn [_ _ _ x] (.countDown latch)))

       (dotimes [_ n]
         (future
           (e/swap! ea inc)))

       (.await latch)

       (is (= n @ea))

       (testing "after release, attempting to read the atom should cause an exception"
         (e/release! ea)
         (is (thrown? AssertionError @ea)))))))

(deftest ^:postgres postgres
  (testing "concurrency of postgres atom"
    (time
     (let [connection-string (get-connection)
           table-name (some-random-table-name)
           n 100]
       (let [ea (with-sql-exception-unwrapping (pg/postgresql-atom 0 connection-string table-name))
             latch (CountDownLatch. n)]

         (add-watch ea :x (fn [_ _ _ x] (.countDown latch)))

         (dotimes [_ n]
           (future
             (e/swap! ea inc)))

         (.await latch)

         (is (= n @ea)))))))

(deftest ^:postgres postgres-atom-semantics
  (let [connection-string (get-connection)
        table-name (some-random-table-name)]
    
    (testing "atom persistence across runs"
      (let [some-value (rand-int 100)
            ea-1 (with-sql-exception-unwrapping (pg/postgresql-atom some-value connection-string table-name))
            ea-2 (with-sql-exception-unwrapping (pg/postgresql-atom 0 connection-string table-name))]
        (is (= some-value @ea-1 @ea-2)
            "all atoms initialized with the same table name should have the value previously stored in that table")
        (with-sql-exception-unwrapping (e/release! ea-1))))

    (testing "releasing an atom"
      (let [ea (with-sql-exception-unwrapping (pg/postgresql-atom 0 connection-string table-name))]
        (with-sql-exception-unwrapping (e/release! ea))
        (is (thrown? AssertionError @ea) "attempting to access an atom after it's been released should raise an Assertionerror")
        (sql/with-connection connection-string
          (is (not (pg/table-exists? table-name)) "releasing a pg-backed atom should delete the table"))))

    (testing "release and persistence across runs"
      (let [first-value (rand-int 100)
            second-value (rand-int 100)
            ea-1 (with-sql-exception-unwrapping (pg/postgresql-atom first-value connection-string table-name))
            _ (e/release! ea-1)
            ea-2 (with-sql-exception-unwrapping (pg/postgresql-atom second-value connection-string table-name))]
        (is (= second-value @ea-2) "the value of a released atom should not be persisted if a later atom is defined with the same table")
        (with-sql-exception-unwrapping (e/release! ea-2))))))

(deftest usage
  (testing "*print-length* unbound"
    (let [file (tmp)
          ea (binding [*print-length* 2]
               (e/file-atom [1 2 3] file :pending-dir "/tmp"))]
      (is (not (.endsWith (slurp file) "...]")))))

  (testing "metadata"
    (let [file (tmp)
           ea (e/file-atom 0 file :pending-dir "/tmp" :meta {:foo "abc"})]
      (is (= {:foo "abc"} (meta ea)))))

  (testing "validation"
    (is (= 0 @(e/file-atom 0 (tmp) :pending-dir "/tmp" :validator even?)))
    (is (thrown? IllegalStateException (e/file-atom 1 (tmp) :validator even?)))
    (is (thrown? IllegalStateException (let [ea (e/file-atom 0 (tmp) :pending-dir "/tmp" :validator #(< % 1))]
                                         (e/swap! ea inc))))
    (let [ea (e/file-atom 0 (tmp) :pending-dir "/tmp" :validator #(< % 10))]
      (is (= 1 (e/swap! ea inc))))))
