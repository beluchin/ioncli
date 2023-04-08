(ns ioncli-daemon.rpc-api
  (:require [potemkin]))

(potemkin/import-vars [ion-clj.simple
                       create-record
                       get-record])

(defn ping [])
