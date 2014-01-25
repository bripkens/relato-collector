(ns impactor.npmjs-to-neo4j
  (:gen-class)
  (:require [impactor.config :refer [config]]

            [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.tools.logging :as logging]
            [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.cypher :as cy]))

(defn- get-project-files []
  (filter #(and (.canRead %)
                (.isFile %)
                (.. % getName (endsWith ".json"))
                (> (.compareToIgnoreCase (.getName %) "tsd-web") 0))
          (file-seq (io/file (get-in config
                                     [:npmjs-collector :target-dir])))))



(def cypher-create-project "MERGE (from:NPMArtifact {name: {name}}) RETURN id(from)")

(def cypher-create-dependency
  "MERGE (from:NPMArtifact {name: {from}})
   MERGE (to:NPMArtifact {name: {to}})
   MERGE (from)-[:DEPENDS {type: {deptype}}]->(to)")


(def cypher-add-tag
  "MERGE (from:NPMArtifact {name: {from}})
   MERGE (to:Tag {name: {tag}})
   MERGE (from)<-[:DESCRIBES]-(to)")


(defn- put-projects-into-neo4j []
  (nr/connect! "http://localhost:7474/db/data/")

  (doseq [project-file (get-project-files)]
    (logging/info "Handling " (.getName project-file))
    (let [project-descriptor (json/parse-string (slurp project-file))
          project-name (get project-descriptor "name")]

      ; ensure that the project exists in Neo4j. It should exist even if there
      ; aren't any dependencies or tags
      (let [response (cy/tquery cypher-create-project {:name project-name})
            id (get (first response) "id(from)")]
        (logging/info "Project id is" id)

        ; some projects have no versions, wtf?
        (if-let [version (last (vals (get project-descriptor "versions")))]
          (do
            (doseq [dependency (keys (get version "dependencies"))]
              (logging/info "Adding dependency to" dependency)
              (cy/tquery cypher-create-dependency {:from project-name
                                                   :to dependency
                                                   :deptype "runtime"}))
            (doseq [dependency (keys (get version "devDependencies"))]
              (logging/info "Adding dev dependency to" dependency)
              (cy/tquery cypher-create-dependency {:from project-name
                                                   :to dependency
                                                   :deptype "development"}))
            (let [tags (get version "keywords")]
              ; old format - meh!
              (when (not (string? tags))
                (doseq [tag (get version "keywords")]
                  (logging/info "Adding tag" tag)
                  (cy/tquery cypher-add-tag {:from project-name
                                              :tag tag})))))
          (logging/warn "Project" project-name "has no versions!"))))))

(defn -main [& args]
  (put-projects-into-neo4j))
