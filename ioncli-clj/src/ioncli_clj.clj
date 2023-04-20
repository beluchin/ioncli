(ns ioncli-clj
  (:require
   [clojure-watch.core :as fwatch]
   [clojure.string :as str]
   [slacker.client :as slacker]))

(defprotocol ^:private RemoteCalling
  (call-remote
    [client fn-symbol args]
    [client fn-symbol args {:keys [timeout]}]))

;; the (public) functions on this namespace are meant to mirror those you can
;; call from the command line.
;;
;; at this level the env is case sensitive i.e. case-insensitivity has
;; to be enforced elsewhere, if at all.

(declare connect connected? get-conn-status get-daemon-pid get-port matches?
         up-filename)
(defn ensure-connect
  "Connect an ion server if not already connected. If already connected
  to an environment with the given name, it validates the jinit
  settings match"
  ([jinit] (ensure-connect nil jinit))
  ([env jinit]
   (let [conn-status (get-conn-status env)]
     (if (connected? conn-status)
       (if (matches? env jinit)
         (println "already connected - daemon on port:" (get-port env)
                  "pid:" (get-daemon-pid env))
         (do (println "already connected but jinit settings don't match")
             (println "check settings in " (up-filename env))))
       (do (connect env jinit)
           (println "connected - daemon on port:" (get-port env)
                    "pid:" (get-daemon-pid env)))))))

(declare with-client)
(defn create-record
  ([name field->type])
  ([env name field->type]
   #_(call-remote env 'create-record [name field->type])
   (with-client env
     #(when-let [result (call-remote % 'create-record [name field->type])]
        (println result))
     #(println "not connected"))))

(declare with-client)
(defn get-record
  ([name field-coll])
  ([env name field-coll]
   (with-client env
     #(when-let [result (call-remote % 'get-record [name field-coll])]
        (println result))
     #(println "not connected"))))

(def ^:private ^:const Daemon-Jar "resources/ioncli-daemon.jar")

(declare get-available-port up-filename start-local-daemon)
(defn- connect [env jinit]
  (start-local-daemon env (get-available-port) jinit (up-filename env)))

(defn- connected? [conn-status]
  (= :already-connected conn-status))

(defn- ensure-delete [up-filename]
  (clojure.java.io/delete-file up-filename :silently))

(defn- get-available-port []
  (let [s (java.net.ServerSocket. 0)]
    (.close s)
    (.getLocalPort s)))

(declare with-client)
(defn- get-conn-status [env]
  (with-client env (constantly :already-connected) (constantly :not-connected)))

(declare to-map up-filename)
(defn- get-daemon-pid [env]
  (get (to-map (up-filename env)) "daemon.pid"))

(declare to-map up-filename)
(defn- get-port [env]
  (get (to-map (up-filename env)) "daemon.port"))

(declare new-rpc-client)
(defn- is-daemon-responsive? [client]
  (try (do (call-remote client 'ping [] {:timeout 500})
           true)
       (catch Exception _ false)))

(defn- matches? [env jinit]
  (let [ks ["mkv.component" "mkv.cshost" "mkv.csport"]]
    (= (select-keys (to-map (up-filename env)) ks)
       (select-keys (to-map jinit) ks))))

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

(declare new-rpc-client-for-port)
(defn- new-rpc-client [env]
  (when (.exists (clojure.java.io/file (up-filename env)))
    (new-rpc-client-for-port (get-port env))))

(defn- new-rpc-client-for-port [port]
  (let [sc (slacker/slackerc (str "localhost:" port))]
    (reify
      java.lang.AutoCloseable
      (close [_] (slacker/close-slackerc sc))

      RemoteCalling
      (call-remote [client fn-symbol args]
        (call-remote client fn-symbol args {}))
      (call-remote [client fn-symbol args {:keys [timeout] :as opts}]
        (slacker/call-remote
          sc

          ;; the name of the remote namespace 
          'ioncli-daemon.rpc-api
            
          fn-symbol
          args
          opts)))))

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
         (map str/trim)
         (filter #(not (str/starts-with? % "#")))
         (filter #(not (str/blank? %)))
         (map #(str/split % #"="))
         (map (fn [[k v]] [(str/trim k) (str/trim v)]))
         (into {}))))

(defn- up-filename [env]
  (str (str/replace (System/getProperty "user.dir") "\\" "/")
       "/.ioncli-" 
       env))

(defn- with-client [env on-connected on-not-connected]
  (if-let [client (new-rpc-client env)]
    (try (if (is-daemon-responsive? client)
           (on-connected client)
           (on-not-connected))
         (finally (.close client)))
    (on-not-connected)))

(comment 

  ;; 
  (def cs (slacker/slackerc "localhost:8080"))
  (slacker/call-remote sc 'ioncli-daemon.rpc-api 'get-record [:name :field-coll]) ;; => 42
  (slacker/close-slackerc sc)
  (slacker/call-remote sc 'ioncli-daemon.rpc-api 'get-record [:name :field-coll]))
