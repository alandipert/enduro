(set-env!
 :src-paths    #{"src"}
 :dependencies '[[org.clojure/clojure       "1.6.0"         :scope "provided"]
                 [boot/core                 "2.0.0-pre28"   :scope "provided"]
                 [tailrecursion/boot-useful "0.1.3"         :scope "test"]
                 [postgresql                "8.4-702.jdbc4" :scope "test"]
                 [org.clojure/java.jdbc     "0.2.3"         :scope "test"]])

(require '[tailrecursion.boot-useful :refer :all]
         '[clojure.test              :as    t])

(def +version+ "1.2.0")

(useful! +version+)

(defn test-ns
  [predsyms ns]
  (let [pred (apply every-pred identity (map resolve predsyms))
        vars (filter pred (vals (ns-publics (doto ns require))))]
    (binding [t/*report-counters* (ref t/*initial-report-counters*)]
      (let [ns-obj (the-ns ns)]
        (t/do-report {:type :begin-test-ns, :ns ns-obj})
        (t/test-vars vars)
        (t/do-report {:type :end-test-ns, :ns ns-obj}))
      @t/*report-counters*)))

(deftask tests
  "Run tests using clojure.test"
  [d dirs NAME #{str} "Source directories containing tests to add to the classpath."
   n namespaces NAMESPACE #{sym} "Symbols of the namespaces to run tests in."
   p preds PRED #{sym} "Namespaced symbols of Var predicates.  Only Vars representing tests for which every PRED is true will be run."]
  (with-pre-wrap
    (when (seq dirs) (set-env! :src-paths #(into % dirs)))
    (if (seq namespaces)
      (let [ns-results (map (partial test-ns preds) namespaces)
            summary    (-> (reduce (partial merge-with +) ns-results)
                           (assoc :type :summary)
                           (doto t/do-report))]
        (when (> (:fail summary) 0) (System/exit 1)))
      (println "No namespaces were tested."))))

(def not-postgres (comp not :postgres meta))

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
        :preds      '#{boot.user/not-postgres}])
