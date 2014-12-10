(set-env!
 :src-paths    #{"src" "test"}
 :dependencies '[[org.clojure/clojure       "1.6.0"         :scope "provided"]
                 [boot/core                 "2.0.0-pre28"   :scope "provided"]
                 [tailrecursion/boot-useful "0.1.3"         :scope "test"]
                 [postgresql                "8.4-702.jdbc4" :scope "test"]
                 [org.clojure/java.jdbc     "0.2.3"         :scope "test"]])

(require '[tailrecursion.boot-useful :refer :all]
         '[boot.pod                  :as    pod])

(def +version+ "1.2.0")

(useful! +version+)

;;; Do this because we want the name 'test', but can't :refer-clojure
;;; :exclude [test] as we're in an implicit boot.user ns here in build.boot.
(ns-unmap 'boot.user 'test)
(ns-unmap 'boot.user 'repeat)

(deftask repeat
  "Run stuff n times, for benchmarking."
  [n num-times NUM int "Number of times to run, 1 by default."]
  (fn [handler]
    (fn [event]
      (dotimes [_ (or num-times 1)]
        (handler event)))))

(defn make-testpod []
  (doto (pod/make-pod (get-env))
    (pod/eval-in
     (require '[clojure.test :as t])
     (defn test-ns* [pred ns]
       (binding [t/*report-counters* (ref t/*initial-report-counters*)]
         (let [ns-obj (the-ns ns)]
           (t/do-report {:type :begin-test-ns :ns ns-obj})
           (t/test-vars (filter pred (vals (ns-publics ns))))
           (t/do-report {:type :end-test-ns :ns ns-obj}))
         @t/*report-counters*)))))

(deftask test
  "Run clojure.test tests in a pod."
  [n namespaces NAMESPACE #{sym} "Symbols of the namespaces to run tests in."
   f filters EXPR #{any} "Clojure expressions that are evaluated with % bound to a Var in a namespace under test.  All must evaluate to true for a Var to be considered for testing by clojure.test/test-vars."
   p fresh-pod bool "Use a fresh pod for every test run (slower, but maybe avoids weird bugs?)"]
  (let [worker-pod (if (not fresh-pod) (make-testpod))]
    (with-pre-wrap
      (if (seq namespaces)
        (let [pod      (or worker-pod (make-testpod))
              filterf `(~'fn [~'%] (and ~@filters))
              summary  (pod/eval-in pod
                         (doseq [ns '~namespaces] (require ns :reload-all))
                         (let [ns-results (map (partial test-ns* ~filterf) '~namespaces)]
                           (-> (reduce (partial merge-with +) ns-results)
                               (assoc :type :summary)
                               (doto t/do-report))))]
          (when (> (apply + (map summary [:fail :error])) 0)
            (throw (ex-info "Some tests failed or errored" summary))))
        (println "No namespaces were tested.")))))

(task-options!
 pom  [:project     'alandipert/enduro
       :version     +version+
       :description "Durable atoms for Clojure"
       :url         "https://github.com/alandipert/enduro"
       :scm         {:url "https://github.com/alandipert/enduro"}
       :license     {:name "Eclipse Public License"
                     :url  "http://www.eclipse.org/legal/epl-v10.html"}]
 test [:namespaces '#{alandipert.enduro-test}
       :filters    '#{((comp not :postgres meta) %)}])
