(ns alandipert.enduro-test
  (:use clojure.test)
  (:require [alandipert.enduro :as e]
            [clojure.java.io :as io])
  (:import java.io.File
           java.util.concurrent.CountDownLatch))

;;; core and file-atom tests

(defn tmp []
  (File/createTempFile "enduro" ".clj"))

(deftest initial-value
  (testing "initialization of file"
    (time
     (let [file (tmp)
           ea (e/file-atom ["testing" 1 2 3] file :pending-dir "/tmp")]
       (println "Wrote to" file)
       (is (= ["testing" 1 2 3] (e/read-file file)))))))

(deftest writes
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

       (.await latch 3000 java.util.concurrent.TimeUnit/MILLISECONDS)

       (is (= n (e/read-file file))))))

  (testing "concurrency of mem atom"
    (time
     (let [ea (e/mem-atom 0)
           n 100
           latch (CountDownLatch. n)]

       (add-watch ea :x (fn [_ _ _ x] (.countDown latch)))

       (dotimes [_ n]
         (future
           (e/swap! ea inc)))

       (.await latch 3000 java.util.concurrent.TimeUnit/MILLISECONDS)

       (is (= n @ea))))))

(deftest usage
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
