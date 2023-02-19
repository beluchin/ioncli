(ns ioncli-daemon.core
  (:gen-class))

(declare touch)
(defn -main [& [jinit port up-filename]]
  (Thread/sleep 5000)
  (touch up-filename))

(defn- touch [filename]
  (with-open [w (clojure.java.io/writer filename)]
    (.write w "up")))
