(set-env!
 :src-paths    #{"src" "test"}
 :dependencies '[[org.clojure/clojure       "1.6.0"         :scope "provided"]
                 [boot/core                 "2.0.0-pre28"   :scope "provided"]
                 [tailrecursion/boot-useful "0.1.3"         :scope "test"]
                 [postgresql                "8.4-702.jdbc4" :scope "test"]
                 [org.clojure/java.jdbc     "0.2.3"         :scope "test"]
                 [adzerk/boot-test          "1.0.0"         :scope "test"]])

(require '[tailrecursion.boot-useful :refer :all]
         '[adzerk.boot-test          :refer [test]])

(def +version+ "1.2.0")

(useful! +version+)

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
