(ns chlorine.reader-test
  (:use [chlorine.reader]
        [clojure.test]))

(deftest member-form?-tests
  (is (= (member-form? '.foo)
         true))
  (is (= (member-form? '.-bar)
         true)))

(deftest reserved-symbol?-test
  (is (reserved-symbol? ["a" "b" #"c"] "a"))
  (is (reserved-symbol? ["a" "b" #"c"] "c"))
  (is (not (reserved-symbol? ["a" "b" #"c"] "e")))
  (is (not (reserved-symbol? ["a" "b" #"c"] "f"))))
