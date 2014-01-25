(ns impactor.ObservableThreadPoolExecutor
  (:gen-class
    :extends java.util.concurrent.ThreadPoolExecutor
    :main false
    :init init
    :constructors {[java.util.Map]
                   [int
                    int
                    long
                    java.util.concurrent.TimeUnit
                    java.util.concurrent.BlockingQueue]}
    :state state)
  (:require [clojure.java.jmx :as jmx])
  (:import [java.util.concurrent TimeUnit LinkedBlockingQueue]))

(defn -init [{:keys [core-pool-size
                     maximum-pool-size
                     keep-alive-time
                     unit
                     jmx-domain
                     jmx-bean-name]}]

  (assert (>= core-pool-size 0))
  (assert (> maximum-pool-size 0))
  (assert (>= maximum-pool-size core-pool-size))
  (assert (>= keep-alive-time 0))
  (assert (not (nil? unit)))
  (assert (not (empty? jmx-domain)))
  (assert (not (empty? jmx-bean-name)))

  (let [state (ref {:queued 0 :running 0 :failed 0 :succeeded 0})]
    (jmx/register-mbean (jmx/create-bean state)
                        (format "%s:name=%s" jmx-domain jmx-bean-name))
    [[core-pool-size
      maximum-pool-size
      keep-alive-time
      unit
      (LinkedBlockingQueue.)]
     state]))

(defn -beforeExecute [this _ _]
  (dosync
    (alter
      (.state this)
      #(-> %
           (assoc :running (inc (:running %)))
           (assoc :queued (.size (.getQueue this)))))))

(defn -afterExecute [this _ throwable]
  (dosync
    (alter
      (.state this)
      (fn [state]
        (let [tmp-state (assoc state :running (dec (:running state))
                                     :queued (.size (.getQueue this)))
              key-to-inc (if (nil? throwable) :succeeded :failed)]
          (assoc tmp-state key-to-inc (inc (key-to-inc tmp-state))))))))
