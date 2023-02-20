(ns ioncli-clj
  (:require [clojure-watch.core :refer [start-watch]]
            [clojure.string :as str]))

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

(defn- connected? [conn-status]
  (= :already-connected conn-status))

(defn- error? [conn-status]
  (or (= :env-in-use conn-status)
      (= :component-in-use conn-status)))

(defn- get-available-port []
  (let [s (java.net.ServerSocket. 0)]
    (.close s)
    (.getLocalPort s)))

(defn- get-conn-status [envname jinit] :not-connected)

(declare to-map)
(defn- get-up-filename [jinit]
  (let [m (to-map jinit)]
    (str (str/replace (System/getProperty "java.io.tmpdir") "\\" "/")
         (str/join "."
                   ["ioncli-daemon"
                    (get m "mkv.component")
                    (get m "mkv.cshost")
                    (get m "mkv.csport")]))))

(defn- ensure-delete [up-filename]
  (clojure.java.io/delete-file up-filename :silently))

(defn- monitor-file [up-filename latch]
  (ensure-delete up-filename)
  (start-watch [{:path (path-to up-filename)
                 :event-types [:create]
                 :callback (fn [_ abs-path]
                             (when (= (java.io.File. abs-path)
                                      (java.io.File. up-filename))
                               (.countDown latch)))
                 :options {:recursive false}}]))

(declare start-local-daemon-async)
(defn- start-local-daemon
  "up-filename is the absolute path to file to be created when daemon is ready.
  This function is not thread-safe. Blocks until daemon is
  ready. Returns nil"
  [jinit port up-filename]
  (let [latch (java.util.concurrent.CountDownLatch. 1)]
    (monitor-file up-filename latch)
    (start-local-daemon-async jinit port up-filename)
    (.await latch)))
 
(defn- start-local-daemon-async [jinit port up-filename]
  (let [status (clojure.java.shell/sh
                 "java" "-jar" Daemon-Jar
                 jinit (str port) up-filename)]
    (when (not= 0 (:exit status))
      (throw (RuntimeException. (:err status))))))

(defn- to-map [props-filename]
  (let [contents (slurp props-filename)]
    (->> (str/split contents #"\n")
         (map #(str/split % #"="))
         (map (fn [[k v]] [(str/trim k) (str/trim v)]))
         (into {}))))

(defn- path-to [filename-abs]
  {:pre [(> (count (re-seq #"/" filename-abs)) 1)]}
  (->> (.split filename-abs "/")
       drop-last
       (clojure.string/join "/")))
