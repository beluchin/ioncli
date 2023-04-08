(ns ion-clj.simple)

(defn connect [jinit] (Thread/sleep 3000))

(defn create-record [name field->type])

(defn get-record
  "returns a map or nil if the record does not exist"
  [name field-coll]
  {"Id" "A_RECORD"
   "Field" 42})
