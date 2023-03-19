(ns ioncli-daemon.rpc-api)

(defn get-record
  "returns a map or nil if the record does not exist"
  [name field-coll]
  {"Id" "A_RECORD"
   "Field" 42})
