(defproject dragon-bookmark-manager "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.866"]
                 [cljsjs/react "17.0.2-0"]
                 [cljsjs/react-dom "17.0.2-0"]
                 [reagent "1.1.0"]
                 [re-frame "1.2.0"]]

  :target-path "target/%s"
  
  :plugins [[lein-cljsbuild "1.1.8"]]

  :cljsbuild {:builds
              [{:id "default"
                :source-paths ["src"]
                :compiler {:externs ["chrome.js" "externs.js"] 
                           ;:parallel-build true ;; decreased compiler time from 84 seconds to 60 seconds
                           ;:cache-analysis true
                           ;:recompile-dependents false
                           ;;; above compiler optimizations cut total compile time from 84 to 40 seconds
                           ;:optimizations :whitespace ;; :whitespace much faster than :simple 
                           ;;; :optimizations :simple ;; success 2405KB output file 
                           :optimizations :advanced ;; success 608KB output file
                           :output-to  "chrome-dragon-bookmark-manager/webpage/js/release.js"
                           :output-dir "chrome-dragon-bookmark-manager/webpage/js/out"
                           }}]}

  :profiles {:dev 
             {:dependencies [[com.bhauman/figwheel-main "0.2.13"]
                             [com.bhauman/rebel-readline-cljs "0.1.4"]]
              :resource-paths ["target" "."]}}

  :aliases {"fig" ["trampoline" "run" "-m" "figwheel.main"]
            "build-dev" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]}

  :clean-targets ^{:protect false} ["target" "chrome-dragon-bookmark-manager/webpage/js"])



