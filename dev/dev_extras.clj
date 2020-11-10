(ns  ^{:clojure.tools.namespace.repl/load false} dev-extras
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
    [clojure.java.javadoc :refer [javadoc]]
    [clojure.pprint :refer [pprint]]
    [clojure.reflect :refer [reflect]]
  ;;  [clojure.repl :refer [apropos dir doc find-doc pst source]]
    [clojure.tools.namespace.repl :refer [refresh refresh-all]]
    [voip.core.kernel :as kernel]
    [clojure.test :refer [run-tests]]
    [voip.core.util :as util]
    [io.pedestal.log :refer [info warn]]
  ;;  [alexandermann.unclogging :refer [merge-config!]]
  ;;  [io.aviso.ansi :as ansi]
    )
  (:import  [io.netty.util.internal.logging
            InternalLoggerFactory
            Slf4JLoggerFactory]))
;;
;; fix java.util.logger
(when (try
        (Class/forName "org.slf4j.bridge.SLF4JBridgeHandler")
        (catch ClassNotFoundException _
          false))
  (eval
    `(do
       (org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
       (org.slf4j.bridge.SLF4JBridgeHandler/install))))

(defn- bridge-netty-to-pedastal
  "In order to get Netty to behave and use SLF4J you have to hook the
  logger factories up to each other. This fn performs that operation.

  Relevant links to this can be found:
  https://gist.github.com/isopov/4368608#file-nettylogginghandlertest-java-L37-L48"
  []
  (InternalLoggerFactory/setDefaultFactory (Slf4JLoggerFactory.)))
(bridge-netty-to-pedastal)

(when (try
        (require 'figwheel.main.logging)
        true
        (catch Throwable _))
  ;; Undo default logger being extremely fine grained in figwheel,
  ;; in order to configure figwheel to delegate to slf4j.
  (let [l @(resolve 'figwheel.main.logging/*logger*)]
    ((resolve 'figwheel.main.logging/remove-handlers) l)
    (.setUseParentHandlers l true)))

(defn set-logging!
  "Sets logging level for all java based on config
  log-config is map with key :level aka {:level :info}"
  [log-config]
;;  (merge-config! log-config)
  )

(def args [])

(def repl-instance nil)

(defn init
  "Creates and initialises the system under development in the Var
  #'system."
  [config]
  (let [;;logging-db  (set-logging! config)
        server-args ["server" (:port config) ]
        client-args ["client" "localhost" (:port config) (str (util/aquire-port (into '() (map #(+ 9001 %) (range 3)))))]]
    (alter-var-root #'repl-instance (constantly 
                                      (do
                                        (util/prompt
                                          #(do
                                            (alter-var-root
                                              #'args
                                              (constantly (if (or (= % "s") (= % "")) server-args (vec (conj client-args %)))))
                                            false)
                                          "client [hostname] or server [s]: (s) ")
                                        (apply kernel/init args))))
))

(defn start
  "Starts the system running, updates the Var #'system."
  []
  (kernel/start repl-instance))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (try
    (kernel/stop repl-instance)
    (catch Exception e))
  (alter-var-root #'repl-instance (constantly nil)))

(defn go
  "Initialises and starts the system running."
  []
  (init)
  (start))

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (if (not (nil? repl-instance)) (stop))
  (refresh :after `go))


(defn- test-namespaces
  []
  (keep (fn [[ns vars]]
          (when (some (comp :test meta) vars) ns))
        (map (juxt identity (comp vals ns-publics))
             (all-ns))))

(defn test-all
  "Run all tests"
  []
  (warn :run/testing {:ok :now})
  (apply run-tests (test-namespaces)))

(defn reset-and-test
  "Reset the system, and run all tests."
  []
  (reset)
  (time (test-all)))
