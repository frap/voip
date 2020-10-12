(ns voip.core.handler
  (:require [gloss.core :refer [defcodec header string]]
            [gloss.io :as io]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [clojure.edn :as edn]
            [aleph.tcp :as tcp])
  (:import java.nio.ByteBuffer))

(def p (string :utf-8 :delimiters " "))
(def lp (string :utf-8 :delimiters ["\r\n"]))

(defcodec PUTC ["PUT" p p lp])
(defcodec LSAC ["LSA" lp])
(defcodec REPC ["REP" lp])
(defcodec LSRC ["LSR" (string :utf-8 :suffix " ")
                      (string :utf-8 :suffix " ")
                      (string :utf-8 :suffix " ")
                      (string :utf-8 :suffix "\r\n")])

(defcodec ERRC (string :utf-8))

(defcodec CMDS
  (header
    p
    (fn [h] (condp = h
    	"PUT" PUTC
    	"LSA" LSAC
    	"REP" REPC
    	"LSR" LSRC
    	ERRC))
    (fn [b] (first b))))

(defn wrap-duplex-stream
  [protocol s]
  (let [out (s/stream)]
    (s/connect
      (s/map #(io/encode protocol %) out)
      s)
    (s/splice
      out
      (io/decode-stream s protocol))))

(defn echo [& args]
  (apply prn args))
  
(defn handler
  "TCP Handler. Decodes the issued command and calls the appropriate
  function to excetion some action."
  [ch ci]
  (receive-all ch
    (fn [b]
      (let [deced (decode CMDS b)]
        (println "Processing command: " deced)
        (condp = (first deced)
          "PUT" (put-fact (rest deced) ch)
          "LSA" (list-facts (second deced) ch)
          (handle-err ch ci))))))

(defn start-server
  [handler port]
  (tcp/start-server
    (fn [s info]
      (handler (wrap-duplex-stream CMDS s) info))
    {:port port}))

(defn echo-handler [f]
  (fn [s info]
    (s/connect (s/map f s)
               s)))
(defn client
  [host port]
  (d/chain (tcp/client {:host host, :port port})
           #(wrap-duplex-stream CMDS %)))

(def s (start-server (echo-handler clojure.string/capitalize)
                     10000))

(def c @(client "localhost" 10000))


(aleph.tcp/start-server handler {:port 10000 :frame CMDS })
