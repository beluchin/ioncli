(ns ioncli-daemon
  (:gen-class)
  (:require
   [clojure.string :as str]
   [ion-clj.simple :as ion]
   [slacker.server :as slacker]))

(declare init-rpc-server create)
(defn -main
  "returns the slacker server to be able to stop it from the REPL"
  [& [jinit port-str up-filename]]
  (let [port (Integer/parseInt port-str)
  
		;; the rpc server keeps the process running
		;;
		;; not sure whether terminating the rpc server 
		;; causes the program to terminate
        server (init-rpc-server port)]
		
    (ion/connect jinit)
    (create up-filename port jinit)
    server))

(defn- init-rpc-server [port]
  (require 'ioncli-daemon.rpc-api)
  (slacker/start-slacker-server [(the-ns 'ioncli-daemon.rpc-api)] port))

(defn- create [filename port jinit]
  (with-open [w (clojure.java.io/writer filename)]
    (.write w (str/join "\n" [(slurp jinit)
                              (str "daemon.port=" port)]))))
