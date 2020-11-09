(ns voip.core.sip)


(defn parse-via [req]
  (re-seq #"SIP\s*\/\s*(\d+\.\d+)\s*\/\s*([\S]+)\s+([^\s;:]+)(?:\s*:\s*(\d+))?" req))

(defn parse-from [request]
  (let [[_ from] (re-seq #"From[\s]?:[\s]?([\w\d-\.]+@[\w-\.]+)" request )]
    from ))

(defn parse-header[request]
  (let [[_ inviteuser] (re-seq #"INVITE[\s]?sip:([\w\d-\.]+@[\w-\.]+)" request)
        from  (parse-from request)]
    {:invite {:to inviteuser
              :from from }}))

