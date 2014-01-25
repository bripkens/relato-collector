(ns impactor.core
  (:gen-class)
  (:require [impactor.config :refer [config]]
            [impactor.maven-central-collector :as mcc]
            
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]
            [clojurewerkz.neocons.rest.labels :as nl]
            [clojurewerkz.neocons.rest.relationships :as nrl]
            [clojurewerkz.neocons.rest.cypher :as cy]))


(def create-cypher "MERGE (from:MavenArtifact {groupId: {fromprops}.groupId, artifactId: {fromprops}.artifactId})
                    MERGE (to:MavenArtifact {groupId: {toprops}.groupId, artifactId: {toprops}.artifactId})
                    MERGE (from)-[:DEPENDS]->(to)")


(defn -main []
  (nr/connect! "http://localhost:7474/db/data/")
  
  (let [coordinates (mapcat #(mcc/query %
                                        (get-in config [:maven-collector :rows])
                                        2000)
                            (get-in config [:maven-collector :start-queries]))]
    (doseq [c coordinates]
      (println "Loading dependencies for " c)
      (doseq [dep (mcc/get-dependencies c)]
        (println "Adding dependency to " dep)
        (cy/tquery create-cypher {:fromprops (dissoc c :version)
                                  :toprops (dissoc dep :version)})))))
