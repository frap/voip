(ns voip.core.comms)

(defn message
  "Create a coordination message"
  [type msg]
  (assoc msg :type type))

