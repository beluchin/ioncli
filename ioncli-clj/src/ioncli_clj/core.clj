
(ns ioncli-clj.core
  (:gen-class)
  (:require ioncli-clj))

(declare ->form)
(defn -main [& args]
  (let [form (->form args)]
    (eval form)

    ;; the file monitoring service uses futures. Hence, we need to
    ;; terminate them explicitly - here instead of in the ensure-connect
    ;; function, because the function may run inside the REPL.
    ;; https://stackoverflow.com/a/27014732/614800
    (shutdown-agents)))

(declare kwds-as-strs)
(defn- ->form [args]
  (let [fn-form (read-string (str "ioncli-clj/" (first args)))
        arg-forms (map #(-> %
                            read-string
                            ((fn [x] (cond
                                       (symbol? x) %
                                       (coll? x) (kwds-as-strs x)
                                       :else x))))
                       (rest args))]
    (reverse (into `(~fn-form) arg-forms))))

(defn- kwds-as-strs [coll] (map #(or (and (keyword? %) (name %)) %) coll))
