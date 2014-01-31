(defproject chlorine/js "1.6.4-SNAPSHOT"
  :description "A naive Clojure to Javascript translator"
  :url "http://github.com/chlorinejs/chlorine"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [pathetic "0.4.0" :exclusions [org.clojure/clojure]]
                 [chlorine-utils "1.2.0"]
                 [hiccup "1.0.5"]
                 [slingshot "0.10.3"]
                 [org.clojure/tools.reader "0.8.3"]])
