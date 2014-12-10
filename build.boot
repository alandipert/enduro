(set-env!
 :src-paths    #{"src"}
 :dependencies '[[org.clojure/clojure       "1.6.0"         :scope "provided"]
                 [boot/core                 "2.0.0-pre28"   :scope "provided"]
                 [tailrecursion/boot-useful "0.1.3"         :scope "test"]
                 [postgresql                "8.4-702.jdbc4" :scope "test"]
                 [org.clojure/java.jdbc     "0.2.3"         :scope "test"]])

(require '[tailrecursion.boot-useful :refer :all]
         '[boot.pod                  :as    pod])

(def +version+ "1.2.0")

(useful! +version+)

(deftask tests
  "Run tests in a pod using clojure.test"
  [d dirs NAME #{str} "Source directories containing tests to add to the classpath."
   n namespaces NAMESPACE #{sym} "Symbols of the namespaces to run tests in."
   p preds PRED #{str} "Clojure expressions that are evaluated with % bound to a Var in a namespace under test.  All must evaluate to true for a Var to be considered for testing by clojure.test/test-vars."]
  (with-pre-wrap
    (if (seq namespaces)
      (let [pod     (pod/make-pod (update-in (get-env) [:src-paths] into dirs))
            predf  `(~'fn [~'%] (and true ~@(map read-string preds)))
            summary (pod/eval-in pod
                      (require '[clojure.test :as t])
                      (doseq [ns '~namespaces] (require ns))
                      (defn test-ns* [pred ns]
                        (binding [t/*report-counters* (ref t/*initial-report-counters*)]
                          (let [ns-obj (the-ns ns)]
                            (t/do-report {:type :begin-test-ns :ns ns-obj})
                            (t/test-vars (filter pred (vals (ns-publics ns))))
                            (t/do-report {:type :end-test-ns :ns ns-obj}))
                          @t/*report-counters*))
                      (let [ns-results (map (partial test-ns* ~predf) '~namespaces)]
                        (-> (reduce (partial merge-with +) ns-results)
                            (assoc :type :summary)
                            (doto t/do-report))))]
        (when (> (apply + (map summary [:fail :error])) 0) (System/exit 1)))
      (println "No namespaces were tested."))))

(task-options!
 pom  [:project     'alandipert/enduro
       :version     +version+
       :description "Durable atoms for Clojure"
       :url         "https://github.com/alandipert/enduro"
       :scm         {:url "https://github.com/alandipert/enduro"}
       :license     {:name "Eclipse Public License"
                     :url  "http://www.eclipse.org/legal/epl-v10.html"}]
 tests [:dirs       '#{"test"}
        :namespaces '#{alandipert.enduro-test}
        :preds      '#{"((comp not :postgres meta) %)"}])
