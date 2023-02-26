(ns ioncli-daemon
  (:require [ion-clj.simple :as ion]
            [slacker.server :as slacker]
            ioncli-daemon.rpc-api)
  (:gen-class))

(declare init-rpc-server create)
(defn -main [& [jinit port-str up-filename]]
  (let [port (Integer/parseInt port-str)
        server (init-rpc-server port)]
    (ion/connect jinit)
    (create up-filename port jinit)

    ;; return the slacker server to be able to stop it from the REPL
    server))

(defn- init-rpc-server [port]
  (slacker/start-slacker-server [(the-ns 'ioncli-daemon.rpc-api)] port))

(defn- create [filename port jinit]
  (with-open [w (clojure.java.io/writer filename)]
    (.write w (str "daemon.port=" port))))
