(ns ioncli-clj.core
  (:gen-class)
  (:require
   [slacker.client :as slacker]))

(declare ->form)
(defn -main [& args]
  (let [form (->form args)]
    (eval form)

    (slacker/shutdown-slacker-client-factory)
    ;; https://stackoverflow.com/a/27014732/614800
    (shutdown-agents)))

(declare kwds-as-strs)
(defn- ->form [args]
  (require 'ioncli-clj)
  (import 'com.iontrading.jmkv.MkvFieldType)
  (let [fn-form (read-string (str "ioncli-clj/" (first args)))
        arg-forms (map #(-> %
                            read-string
                            ((fn [x] (cond
                                       (symbol? x) %
                                       (coll? x) (kwds-as-strs x)
                                       :else x))))
                       (rest args))]
    (reverse (into `(~fn-form) arg-forms))))

(defn- kwds-as-strs [coll]
  (letfn [(kwd-as-str [x] (if (keyword? x) (name x) x))]
    (cond
      (map? coll) (update-keys coll kwd-as-str)
      :else (vec (map kwd-as-str coll)))))
