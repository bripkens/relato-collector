(defproject impactor "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]

                 ; Monitoring
                 [org.clojure/java.jmx "0.2.0"]

                 ; logging
                 [org.clojure/tools.logging "0.2.6"]
                 [log4j/log4j "1.2.17"]

                 ; json
                 [cheshire "5.3.0"]

                 ; xml
                 [org.clojure/data.zip "0.1.1"]

                 ; http access
                 [clj-http "0.7.8"]

                 ; neo4j access
                 [clojurewerkz/neocons "2.0.0"]]
  :aot [impactor.ObservableThreadPoolExecutor
        impactor.npmjs-collector
        impactor.npmjs-to-neo4j]
  :jvm-opts ["-Xmx1g"
             "-server"]
  :main impactor.npmjs-to-neo4j)
