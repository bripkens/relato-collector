(ns impactor.github-collector
  (:gen-class)
  (:require [impactor.config :refer [config]]

            [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.tools.logging :as logging]))

(def
  ^:private
  api-base
  "https://api.github.com")

(def
  ^:private
  rate-limit
  (atom {:limit 0
         :remaining 0
         :reset 0}))

(defn- extract-rate-limit [response]
  (let [{headers :headers} response]
    (swap! rate-limit
           (fn [_] {:limit (get headers "x-ratelimit-limit" 0)
                    :remaining (get headers "x-ratelimit-remaining" 0)
                    :reset (get headers "x-ratelimit-reset" 0)}))
    response))

(defn- rget [p]
  (extract-rate-limit
    (client/get
      (str api-base p)
      {:basic-auth [(get-in config [:github-collector :user])
                    (get-in config [:github-collector :password])]
       :socket-timeout 10000
       :conn-timeout 10000})))

(defn- get-rate-limit [] (rget "/rate_limit"))

(defn- get-repositories [since]
  (rget (str "/repositories?since=" since)))

(defn- write [p contents]
  (with-open [writer (io/writer p)]
          (.write writer contents)))

(defn- collect-repository-overview-data [since]
  (try
    (let [response (get-repositories since)
          body (json/parse-string (:body response))
          start (get (first body) "id")
          end (get (last body) "id")]
      (write (str (get-in config [:github-collector :target])
                  "/"
                  start
                  "-"
                  end
                  ".json")
             (:body response))
      (logging/info "Got repos" start "to" end)
      (collect-repository-overview-data end))
    (catch Exception e
      (logging/error "Error occured retrieving repository overview for start"
                     since
                     ". Will retry in five minutes."
                     e)
      (Thread/sleep 18000)
      (collect-repository-overview-data since))))

(defn- find-highest-seen-repository-id []
  (let [files (filter #(and (.isFile %) (.endsWith (.getName %) ".json"))
                      (file-seq (io/file (get-in
                                           config
                                           [:github-collector :target]))))
        sorted-files (sort-by #(.lastModified %) files)
        last-file (last sorted-files)]
    (if (nil? last-file)
      0
      (second (re-matches #"^\d+-(\d+)\.json$" (.getName last-file))))))

(defn -main []
  (get-rate-limit)
  (logging/info "Starting rate limit" @rate-limit)
  (let [start (find-highest-seen-repository-id)]
    (logging/info "Starting with repository ID" start)
    (collect-repository-overview-data start)))
