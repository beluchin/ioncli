
(ns ioncli-clj.core
  (:gen-class)
  (:require ioncli-clj))

(defn -main [& args]
  (let [fn-name (first args)
        quoted-fn-args (map #(str "\"" % "\"") (rest args))]
    (eval (read-string
            (format "(ioncli-clj/%s %s)"
                    fn-name
                    (clojure.string/join " " quoted-fn-args))))

    ;; the file monitoring service uses futures. Hence, we need to
    ;; terminate them explicitly - here instead of in the ensure-connect
    ;; function, because the function may run inside the REPL.
    ;; https://stackoverflow.com/a/27014732/614800
    (shutdown-agents)))
