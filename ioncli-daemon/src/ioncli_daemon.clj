(ns ioncli-daemon
  (:gen-class)
  (:require
   [clojure.string :as str]
   [ion-clj.simple :as ion]
   [slacker.server :as slacker]
   [clojure.tools.logging :as log]))

(declare create init-rpc-server pid)
(defn -main
  "returns the slacker server to be able to stop it from the REPL"
  [& [jinit port-str up-filename :as args]]
  (log/info "starting daemon" args)
  (try (let [port (Integer/parseInt port-str)
          
		     ;; the rpc server keeps the process running
		     ;;
		     ;; not sure whether terminating the rpc server 
		     ;; causes the program to terminate
             server (init-rpc-server port)]
	  
         (ion/connect jinit)
         (create up-filename port jinit)
         (log/info "started - pid:" (pid))
         server)
       (catch Exception e
         (log/error e)
         (.getMessage e))))

(declare pid)
(defn- create [filename port jinit]
  (with-open [w (clojure.java.io/writer filename)]
    (let [jinit-settings (->> (str/split-lines (slurp jinit))
                              (map str/trim)
                              (filter #(not (str/starts-with? % "#")))
                              (filter #(not (str/blank? %))))]
      (.write w (str/join "\n"
                          (concat jinit-settings
                                  [(str "daemon.port=" port)
                                   (str "daemon.pid=" (pid))]))))))

(defn- init-rpc-server [port]
  (require 'ioncli-daemon.rpc-api)
  (slacker/start-slacker-server [(the-ns 'ioncli-daemon.rpc-api)] port))

(defn- pid []
  (-> (.getName (java.lang.management.ManagementFactory/getRuntimeMXBean))
      (clojure.string/split #"@")
      first))
