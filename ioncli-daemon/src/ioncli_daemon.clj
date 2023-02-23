(ns ioncli-daemon
  (:require [ion-clj.simple :as ion])
  (:gen-class))

(declare touch)
(defn -main [& [jinit port up-filename]]
  (ion/connect jinit)
  (touch up-filename))

(defn- touch [filename]
  (with-open [w (clojure.java.io/writer filename)]
    (.write w "up")))
