(ns ioncli-daemon
  (:require [ion-clj.simple :as ion]
            [slacker.server :as slacker]
            ioncli-daemon.rpc-api)
  (:gen-class))

(declare init-rpc-server touch)
(defn -main [& [jinit port-str up-filename]]
  (let [server (init-rpc-server (Integer/parseInt port-str))]
    (ion/connect jinit)
    (touch up-filename)

    ;; return the slacker server to be able to stop it from the REPL
    server))

(defn- init-rpc-server [port]
  (slacker/start-slacker-server [(the-ns 'ioncli-daemon.rpc-api)] port))

(defn- touch [filename]
  (with-open [w (clojure.java.io/writer filename)]
    (.write w "up")))
