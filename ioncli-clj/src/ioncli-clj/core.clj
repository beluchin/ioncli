(ns ioncli-clj.core
  (:gen-class)
  (:require ioncli-clj))

(defn -main [& args]
  (let [fn-name (first args)
        quoted-fn-args (map #(str "\"" % "\"") (rest args))]
    (eval (read-string
            (str "(ioncli-clj/" fn-name
                 (clojure.string/join " " quoted-fn-args)
                 ")")))))
