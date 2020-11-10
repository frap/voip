(ns user
  (:require [dev :as dev]
            [clojure.spec.test.alpha :as stest]))

(defn start
  "Load and switch to the 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev)
  (stest/instrument))

(start)
