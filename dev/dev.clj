(ns dev
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
    [clojure.java.javadoc :refer [javadoc]]
    [clojure.pprint :refer [pprint]]
    [clojure.reflect :refer [reflect]]
    [clojure.repl :refer [apropos dir doc find-doc pst source]]
    [clojure.tools.namespace.repl :refer [refresh refresh-all]]
    [voip.core.kernel :as kernel]
    [clojure.test :refer [run-tests]]
    [voip.core.util :as util]
    [alexandermann.unclogging :refer [merge-config!]]
 ;;   [io.aviso.ansi :as ansi]
     ))
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

(when (try
        (require 'figwheel.main.logging)
        true
        (catch Throwable _))
  ;; Undo default logger being extremely fine grained in figwheel,
  ;; in order to configure figwheel to delegate to slf4j.
  (let [l @(resolve 'figwheel.main.logging/*logger*)]
    ((resolve 'figwheel.main.logging/remove-handlers) l)
    (.setUseParentHandlers l true)))


(merge-config! {:level :debug})

(def args [])

(def repl-instance nil)

(defn init
  "Creates and initialises the system under development in the Var
  #'system."
  [config]
  (let [server-args ["server" (:port config) ]
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
                                        (apply kernel/init args))))))

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
  (apply run-tests (test-namespaces)))

(defn reset-and-test
  "Reset the system, and run all tests."
  []
  (reset)
  (time (test-all)))
