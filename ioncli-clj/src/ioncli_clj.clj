(ns ioncli-clj
  (:require
   [clojure-watch.core :as fwatch]
   [clojure.string :as str]
   [slacker.client :as slacker]))

(defprotocol ^:private RemoteCalling
  (call-remote [client fn-symbol args]))

;; the (public) functions on this namespace are meant to mirror those you can
;; call from the command line.
;;
;; at this level the env is case sensitive i.e. case-insensitivity has
;; to be enforced elsewhere, if at all.

(declare connect connected? error? error-str
         get-conn-status get-daemon-pid get-port)
(defn ensure-connect
  "Connect an ion server if not already connected. On the second form,
  to use an anonymous env, pass in nil to env"
  ([jinit] (ensure-connect nil jinit))
  ([env jinit]
   (let [conn-status (get-conn-status env jinit)]
     (if-not (error? conn-status)
       (if-not (connected? conn-status)
         (do (connect env jinit)
             (println "connected - daemon on port:" (get-port env)
                      "pid:" (get-daemon-pid env)))
         (println "already connected - daemon on port:" (get-port env)
                  "pid:" (get-daemon-pid env)))
       (println "error:" (error-str conn-status))))))

(declare new-rpc-client)
(defn get-record
  ([name field-coll])
  ([env name field-coll]
   (with-open [client (new-rpc-client env)]
     (println (call-remote client 'get-record [name field-coll])))))

(def ^:private ^:const Daemon-Jar "resources/ioncli-daemon.jar")

(declare get-available-port up-filename start-local-daemon)
(defn- connect [env jinit]
  (start-local-daemon env (get-available-port) jinit (up-filename env)))

(defn- connected? [conn-status]
  (= :already-connected conn-status))

(defn- ensure-delete [up-filename]
  (clojure.java.io/delete-file up-filename :silently))

(defn- error? [conn-status]
  (or (= :env-in-use conn-status)
      (= :component-in-use conn-status)))

(defn- error-str [conn-status] (name conn-status))
 
(defn- get-available-port []
  (let [s (java.net.ServerSocket. 0)]
    (.close s)
    (.getLocalPort s)))

(defn- get-conn-status [envname jinit] :not-connected)

(declare to-map up-filename)
(defn- get-daemon-pid [env]
  (get (to-map (up-filename env)) "daemon.pid"))

(declare to-map up-filename)
(defn- get-port [env]
  (get (to-map (up-filename env)) "daemon.port"))

(declare path-to)
(defn- monitor-file
  "returns a no-arg function to call to stop monitoring"
  [up-filename latch]
  (ensure-delete up-filename)
  (fwatch/start-watch [{:path (path-to up-filename)

                          ;; monitoring for :create has a race condition
                          ;; in which the monitor is invoked before the
                          ;; contents of the file are written
                          :event-types [:modify]
                          
                          :callback (fn [_ abs-path]
                                      (when (= (java.io.File. abs-path)
                                               (java.io.File. up-filename))
                                        (.countDown latch)))
                          :options {:recursive false}}]))

(defn- new-rpc-client [env]
  (let [sc (slacker/slackerc (str "localhost:" (get-port env)))]
    (reify
      java.lang.AutoCloseable
      (close [_] (slacker/close-slackerc sc))

      RemoteCalling
      (call-remote [client fn-symbol args]
        (slacker/call-remote
               sc

               ;; the name of the remote namespace 
               'ioncli-daemon.rpc-api
                             
               fn-symbol
               args)))))

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
  [env port jinit up-filename]
  (let [latch (java.util.concurrent.CountDownLatch. 1)
        stop-file-monitor-fn (monitor-file up-filename latch)]
    (try (do (start-local-daemon-async env port jinit up-filename)
             (when-not (.await latch 30 java.util.concurrent.TimeUnit/SECONDS)
               (throw (Exception. "Timed out waiting for daemon to start"))))
         (finally (stop-file-monitor-fn)))))
 
(defn- start-local-daemon-async [env port jinit up-filename]
  ;; could not get clojure.java.shell/sh to work asynch - it blocks
  ;; until the Java process exits
  (.exec (Runtime/getRuntime)
         (into-array String
                     ["cmd" "/C" "start" "/B"
                      "java" (str "-DIONCLI_DAEMON_ENV=" env) 
                      "-jar" Daemon-Jar
                      jinit (str port) up-filename])))

(defn- to-map [props-filename]
  (let [contents (slurp props-filename)]
    (->> (str/split contents #"\n")
         (map #(str/split % #"="))
         (map (fn [[k v]] [(str/trim k) (str/trim v)]))
         (into {}))))

(defn- up-filename [env]
  (str (str/replace (System/getProperty "user.dir") "\\" "/")
       "/.ioncli-" 
       env))

(comment 

  ;; 
  (def cs (slacker/slackerc "localhost:8080"))
  (slacker/call-remote sc 'ioncli-daemon.rpc-api 'get-record [:name :field-coll]) ;; => 42
  (slacker/close-slackerc sc)
  (slacker/call-remote sc 'ioncli-daemon.rpc-api 'get-record [:name :field-coll]))
