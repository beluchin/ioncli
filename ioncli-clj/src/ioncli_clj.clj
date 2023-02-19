(ns ioncli-clj
  (:require [clojure-watch.core :refer [start-watch]]))

(declare connect connected? error? get-conn-status)
(defn ensure-connect
  "does not connect if already connected.
  returns one of :already-connected | :connected | :env-in-use | :component-in-use"
  ([envname-or-jinit])
  ([envname jinit]
   (let [conn-status (get-conn-status envname jinit)]
     (if-not (error? conn-status)
       (if-not (connected? conn-status)
         (do (connect envname jinit) :connected)
         conn-status)
       conn-status))))

(def ^:private Daemon-Jar "resources/ioncli-daemon.jar")

(declare get-available-port get-up-filename start-local-daemon)
(defn- connect [envname jinit]
  (start-local-daemon jinit
                      (get-available-port)
                      (get-up-filename jinit)))

(defn- dir [filename-abs]
  {:pre [(> (count (re-seq #"/" filename-abs)) 1)]}
  (->> (.split filename-abs "/")
       drop-last
       (clojure.string/join "/")))

(defn- ensure-delete [up-filename]
  (clojure.java.io/delete-file up-filename :silently))

(declare monitor-file start-local-daemon-async)
(defn- start-local-daemon
  "up-filename is the absolute path to file to be created when daemon is ready.
  This function is not thread-safe. Blocks until daemon is
  ready. Returns nil"
  [jinit port up-filename]
  (let [latch (java.util.concurrent.CountDownLatch. 1)]
    (monitor-file up-filename latch)
    (start-local-daemon-async jinit port up-filename)
    (.await latch)))

(defn- monitor-file [up-filename latch]
  (ensure-delete up-filename)
  (start-watch [{:path (dir up-filename)
                 :event-types [:create]
                 :callback (fn [_ abs-path]
                             (when (= (java.io.File. abs-path)
                                      (java.io.File. up-filename))
                               (.countDown latch)))
                 :options {:recursive false}}]))
  
(defn- start-local-daemon-async [jinit port up-filename]
  (let [status (clojure.java.shell/sh
                 "java" "-jar" Daemon-Jar
                 jinit (str port) up-filename)]
    (when (not= 0 (:exit status))
      (throw (RuntimeException. (:err status))))))
