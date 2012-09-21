(ns alandipert.enduro-test
  (:use clojure.test)
  (:require [alandipert.enduro :as e])
  (:import java.io.File
           java.util.concurrent.CountDownLatch))

(defn tmp []
  (File/createTempFile "enduro" ".clj"))

(deftest initial-value
  (testing "initialization of file"
    (time
     (let [file (tmp)
           ea (e/atom ["testing" 1 2 3] file)]
       (println "Wrote to" file)
       (is (= ["testing" 1 2 3] (e/read-file file)))))))

(deftest writes
  (testing "concurrency"
    (time
     (let [file (tmp)
           ea (e/atom 0 file)
           n 100
           latch (CountDownLatch. n)]

       (add-watch ea :x (fn [_ _ _ x] (.countDown latch)))

       (dotimes [_ n]
         (future
           (e/swap! ea inc)))

       (.await latch 3000 java.util.concurrent.TimeUnit/MILLISECONDS)

       (is (= n (e/read-file file)))))))

(deftest usage
  (testing "metadata"
    (let [file (tmp)
           ea (e/atom 0 file :meta {:foo "abc"})]
      (is (= {:foo "abc"} (meta ea)))))

  (testing "validation"
    (is (= 0 @(e/atom 0 (tmp) :validator even?)))
    (is (thrown? IllegalStateException (e/atom 1 (tmp) :validator even?)))
    (is (thrown? IllegalStateException (let [ea (e/atom 0 (tmp) :validator #(< % 1))]
                                         (e/swap! ea inc))))
    (let [ea (e/atom 0 (tmp) :validator #(< % 10))]
      (is (= 1 (e/swap! ea inc)))))

  (testing "can't have multiple atoms with same file"
    (let [file (tmp)
           ea (e/atom 0 file)]
       (is (thrown? IllegalArgumentException (e/atom 0 file)))))

  (testing "can release and re-use file with release"
    (let [file (tmp)
          a1 (e/atom 0 file)]
      (is (= 1 (e/swap! a1 inc)))
      (e/release! a1)
      (let [a2 (e/atom 0 file)]
        ;; atom initializes with value on disk (1) instead of 2
        (is (= 2 (e/swap! a2 inc))))))

  (testing "derefing after release throws"
    (is (thrown? IllegalStateException
                 (let [file (tmp)
                       a1 (e/atom 0 file)]
                   (e/release! a1)
                   @a1)))))