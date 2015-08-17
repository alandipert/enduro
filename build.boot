(set-env!
 :resource-paths #{"src"}
 :source-paths #{"test"}
 :dependencies '[[adzerk/bootlaces      "0.1.11"        :scope "test"]
                 [postgresql            "8.4-702.jdbc4" :scope "test"]
                 [org.clojure/java.jdbc "0.2.3"         :scope "test"]
                 [adzerk/boot-test      "1.0.4"         :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[adzerk.boot-test :refer [test]])

(def +version+ "1.2.0")

(task-options!
 pom  {:project     'alandipert/enduro
       :version     +version+
       :description "Durable atoms for Clojure"
       :url         "https://github.com/alandipert/enduro"
       :scm         {:url "https://github.com/alandipert/enduro"}
       :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}}
 test {:namespaces '#{alandipert.enduro-test}
       :filters    '#{((comp not :postgres meta) %)}}))
