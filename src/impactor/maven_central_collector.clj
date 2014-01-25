(ns impactor.maven-central-collector
  (:require [impactor.config :as config]
            
            [cheshire.core :as json]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.logging :as logging]
            [clj-http.client :as client]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml->]]))

(defn- is-ok [status]
  (and (>= status 200) (<= status 299)))

(defn- parse-query-result [body]
  (let [parsed-body (json/parse-string body)]
    (map #(identity {:groupId (% "g")
                     :artifactId (% "a")
                     :version (% "latestVersion")})
         (get-in parsed-body ["response" "docs"]))))

(def
  ^:private
  maven-central-file-path
  "http://search.maven.org/remotecontent?filepath=%s/%s/%s/%s-%s.pom")

(defn- get-pom [{groupId :groupId
                 artifactId :artifactId
                 version :version
                 :as coordinates}]
  (try
    (let [uri (format maven-central-file-path
            (string/replace groupId "." "/")
            artifactId
            version
            artifactId
            version)
          {status :status body :body :as response} (client/get uri)]
      (if (is-ok status)
        body
        (do
          (logging/error "Failed to load POM for"
                         coordinates
                         "HTTP response:"
                         response)
          nil)))
    (catch Exception e
      (logging/error "Failed to load POM for"
                         coordinates
                         "Exception:"
                         e)
      nil)))

(defn query [q rows start]
  (let [response (client/get "http://search.maven.org/solrsearch/select"
                             {:query-params {"q" q
                                             "rows" rows
                                             "start" start}})
        {status :status body :body} response]
    (if (is-ok status)
      (parse-query-result body)
      ; stupid error handling
      ; TODO at least log this error
      [])))

;; this must exist in the standard clojure API, but cannot seem to find it :-(
(defn- str-to-steam [s] (java.io.ByteArrayInputStream. (.getBytes s)))

;; Beware - ugly XML parsing...
;; TODO what about dependencies that are defined in a parent POM?
;; TODO what about versions that are defined in a parent POM?
;; TODO what about versions / artifacts that are defined through a property
;;      placeholder mechanism
;; TODO what about plugin dependencies?
;; TODO what about dependencies that should be loaded from repository !=
;;      maven central
;; TODO what about projects that bundle dependencies?
;; TODO what about property placeholders in groupIds or artifactIds?
;;
;; oh boy - Maven just has too many special cases for a first prototype...
(defn get-dependencies [coordinates]
  (try
    
    (if-let [src (get-pom coordinates)]
      (let [parsed-xml (xml/parse (str-to-steam src))
            zipped (zip/xml-zip parsed-xml)]
        ; there could be multiple content elements, so grab the first!
        (map (fn [dependency] (let [content (:content (first dependency))
                                    ; we are not interested in all the tags,
                                    ; filter the rest
                                    coord-elements (filter #(#{:groupId :artifactId :version} (:tag %))
                                                           content)]
                                ; turn XML tags two dimensional arrays (tag value) 
                                ; and then into a hash {key value}
                                (into {} (map #(identity [(:tag %)
                                                          (.trim (first (:content %)))])
                                              coord-elements))))
             (xml-> zipped :dependencies :dependency)))
      ; stupid error handling
      (do
        (logging/info "Could not load dependencies for" coordinates)
        []))
    ; Some Maven artifacts have broken POMs -_-
    ; For instance org.apache.openejb apache-tomcat 7.0.34 cannot be parsed
    ; by the SAX parser
    (catch Exception e
      (logging/error "Exception occured while retrieving dependencies for"
                     coordinates
                     e)
      [])))
