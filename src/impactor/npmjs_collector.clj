(ns impactor.npmjs-collector
  (:gen-class)
  (:require [impactor.config :refer [config]]

            [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.tools.logging :as logging]))

; One can load and store all registered artifact names from NPMJS
; http://registry.npmjs.org/-/all
; and then load each packages' package.json file through
; http://registry.npmjs.org/foo


; TODO replace get-npmjs-metadata.bash once this becomes stable
(defn- get-project-names []
  (-> (get-in config [:npmjs-collector :source])
      io/file
      slurp
      json/parse-string))


(defn load-all-project-files []
  (doseq [project-name (get-project-names)]
    (logging/info "Retrieving project descriptor for" project-name)
    (try
      (let [uri (format "http://registry.npmjs.org/%s" project-name)
            {status :status body :body :as response} (client/get uri)
            output (format (get-in config [:npmjs-collector :target])
                           project-name)]
        (with-open [writer (io/writer output)]
          (.write writer body)))
      (catch Exception e
        (logging/error "Failed to load project file for " project-name e)))))


(defn -main []
  (load-all-project-files))
