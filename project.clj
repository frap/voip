(defproject voip "0.1.1-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [manifold "0.1.9-alpha3"]
                 [org.clojure/core.async "1.3.610"]
                 [byte-streams "0.2.2"]
                 [aleph "0.4.7-alpha5"]
                 [com.taoensso/nippy "3.0.0"]
                 [pandect "0.6.1"]
                 [gloss "0.2.6"]
                 [kovacnica/clojure.network.ip "0.1.3"]
                 [org.clojure/core.match "1.0.0"]]

  :main voip.core.cli

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "1.0.0"]]
                   :source-paths ["dev"]}})
