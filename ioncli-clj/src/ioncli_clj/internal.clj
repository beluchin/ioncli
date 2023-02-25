(ns ioncli-clj.internal)

;; defined separate on this file because if defined (as private) on
;; the parent namespace, we will get:
;;
;; Warning: protocol #'ioncli-clj/RemoteCalling is overwriting
;; function call-remote
(defprotocol RemoteCalling
  (call-remote [client fn-symbol args]))
