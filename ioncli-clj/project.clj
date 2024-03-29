(defproject ioncli-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clojure-watch "LATEST"]
                 [slacker/slacker "LATEST"]
                 [com.iontrading/jmkv "152"]
                 [ch.qos.logback/logback-classic "1.4.5"]]
  :repl-options {:init-ns ioncli-clj}

  :main ^:skip-aot ioncli-clj.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
