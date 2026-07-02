(defproject com.taoensso/tengen "1.2.0"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Simple React component DSL for Clojure/Script"
  :url "https://www.taoensso.com/tengen"

  :license
  {:name "Eclipse Public License - v 1.0"
   :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :test-paths ["test" #_"src"]

  :dependencies
  [[com.taoensso/encore "3.168.0"]
   [com.taoensso/truss  "2.3.0"]]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :provided {:injections   [(println "Lein profile: :provided")]
              :dependencies [[reagent                   "2.0.1"]
                             [org.clojure/clojurescript "1.12.145"]
                             [org.clojure/clojure       "1.12.5"]]}
   :c1.12    {:dependencies [[org.clojure/clojure       "1.12.5"]]}
   :c1.11    {:dependencies [[org.clojure/clojure       "1.11.4"]]}
   :c1.10    {:dependencies [[org.clojure/clojure       "1.10.3"]]}

   :graal-tests
   {:injections   [(println "Lein profile: :graal-tests")]
    :source-paths ["test"]
    :main taoensso.graal-tests
    :aot [taoensso.graal-tests]
    :uberjar-name "graal-tests.jar"
    :dependencies
    [[org.clojure/clojure                  "1.11.4"]
     [com.github.clj-easy/graal-build-time "1.0.6"]]}

   :test
   {:injections [(println "Lein profile: :test")]}

   :cljs-test
   {:global-vars {*warn-on-reflection* false}}

   :dev
   {:injections [(println "Lein profile: :dev")]
    :jvm-opts
    ["-server"
     "-Dtaoensso.elide-deprecated=true"]

    :global-vars
    {*warn-on-reflection* true
     *assert*             true
     *unchecked-math*     false #_:warn-on-boxed}

    :dependencies
    [[org.clojure/test.check "1.1.3"]
     [reagent                "2.0.1"]
     [thheller/shadow-cljs   "3.4.11"]]

    :plugins
    [[lein-pprint    "1.3.2"]
     [lein-ancient   "1.0.0"]]}}

  :aliases
  {"start-dev"  ["with-profile" "+dev" "repl" ":headless"]
   "build-once" ["do" ["clean"] ["test-cljs"]]
   "deploy-lib" ["do" ["build-once"] ["deploy" "clojars"] ["install"]]

   "test-clj"   ["with-profile" "+c1.12:+c1.11:+c1.10" "test"]
   "test-cljs"  ["with-profile" "+c1.12,+cljs-test" "run" "-m" "shadow.cljs.devtools.cli" "compile" "cljs-test"]
   "test-all"   ["do" ["clean"] ["test-clj"] ["test-cljs"]]})
