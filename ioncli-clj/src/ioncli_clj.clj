(ns ioncli-clj
  (:require
   [clojure-watch.core :refer [start-watch]]
   [clojure.string :as str]
   [ioncli-clj.internal :as internal]
   [slacker.client :as slacker]))

;; the (public) functions on this namespace are meant to mirror those you can
;; call from the command line.

(declare connect connected? error? get-conn-status)
(defn ensure-connect
  "Returns one of: 
  :already-connected | :connected | :env-in-use | :component-in-use "
  ([env-or-jinit])
  ([env jinit]
   (let [conn-status (get-conn-status env jinit)]
     (if-not (error? conn-status)
       (if-not (connected? conn-status)
         (do (connect env jinit) :connected)
         conn-status)
       conn-status))))

(declare call-remote new-rpc-client)
(defn get-record
  ([name field-coll])
  ([env name field-coll]
   (with-open [client (new-rpc-client env)]
     (call-remote client 'get-record name field-coll))))

(def ^:private ^:const Daemon-Jar "resources/ioncli-daemon.jar")

(defn- call-remote [client fn-symbol & args]
  (internal/call-remote client fn-symbol args))

(declare get-available-port get-up-filename start-local-daemon)
(defn- connect 
  ([env jinit]
   (let [port (get-available-port)]
     (connect env jinit port)
     (println "started daemon on port" port)))
  ([env jinit port]
   (start-local-daemon port jinit (get-up-filename env))))

(defn- connected? [conn-status]
  (= :already-connected conn-status))

(defn- ensure-delete [up-filename]
  (clojure.java.io/delete-file up-filename :silently))

(defn- error? [conn-status]
  (or (= :env-in-use conn-status)
      (= :component-in-use conn-status)))

(defn- get-available-port []
  (let [s (java.net.ServerSocket. 0)]
    (.close s)
    (.getLocalPort s)))

(defn- get-conn-status [envname jinit] :not-connected)

(defn- get-port [env]
  (get (to-map (get-up-filename env)) "daemon.port"))

(declare to-map)
(defn- get-up-filename [env]
  (str (str/replace (System/getProperty "java.io.tmpdir") "\\" "/")
       ".ioncli-"
       env))

(defn- new-rpc-client [env]
  (let [sc (slacker/slackerc (str "localhost:" (get-port env)))]
    (reify
      java.lang.AutoCloseable
      (close [_] (slacker/close-slackerc sc))

      internal/RemoteCalling
      (call-remote [client fn-symbol args]
        (slacker/call-remote
          sc

          ;; the name of the remote namespace 
          'ioncli-daemon.rpc-api
                             
          fn-symbol
          args)))))

(declare path-to)
(defn- monitor-file [up-filename latch]
  (ensure-delete up-filename)
  (start-watch [{:path (path-to up-filename)
                 :event-types [:create]
                 :callback (fn [_ abs-path]
                             (when (= (java.io.File. abs-path)
                                      (java.io.File. up-filename))
                               (.countDown latch)))
                 :options {:recursive false}}]))

(defn- path-to [filename-abs]
  {:pre [(> (count (re-seq #"/" filename-abs)) 1)]}
  (->> (.split filename-abs "/")
       drop-last
       (clojure.string/join "/")))

(declare start-local-daemon-async)
(defn- start-local-daemon
  "up-filename is the absolute path to file to be created when daemon is ready.
  This function is not thread-safe. Blocks until daemon is
  ready. Returns nil"
  [port jinit up-filename]
  (let [latch (java.util.concurrent.CountDownLatch. 1)]
    (monitor-file up-filename latch)
    (start-local-daemon-async port jinit up-filename)
    (.await latch)))
 
(defn- start-local-daemon-async [port jinit up-filename]
  ;; could not get clojure.java.shell/sh to work asynch - it blocks
  ;; until the Java process exits
  (.exec (Runtime/getRuntime)
         (into-array String
                     ["cmd" "/C" "start" "/B"
                      "java" "-jar" Daemon-Jar
                      jinit (str port) up-filename])))

(defn- to-map [props-filename]
    (let [contents (slurp props-filename)]
      (->> (str/split contents #"\n")
           (map #(str/split % #"="))
           (map (fn [[k v]] [(str/trim k) (str/trim v)]))
           (into {}))))


(comment 

  ;; 
  (def cs (slacker/slackerc "localhost:8080"))
  (slacker/call-remote sc 'ioncli-daemon.rpc-api 'get-record [:name :field-coll]) ;; => 42
  (slacker/close-slackerc sc)
  (slacker/call-remote sc 'ioncli-daemon.rpc-api 'get-record [:name :field-coll]))
