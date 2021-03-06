(ns chlorine.js-test
  (:use [chlorine.js]
        [clojure.test]
        [slingshot.slingshot]
        [chlorine.util]))

(dosync (ref-set *macros* {}))

(deftest ->camelCase-test
  (is (= (->camelCase "foo-bar-boo")
         "fooBarBoo")))

(deftest symbol-alias-tests
  (is (= (binding [*aliases* (ref '{foo fool})]
           (with-out-str
             (emit-symbol 'foo)))
         "fool")))

(deftest track-emitted-symbols-tests
  (is (= (binding [*core-symbols* '#{foo bar boo}
                   *core-symbols-in-use* (ref '#{bazz})]
           (with-out-str
             (emit-symbol 'foo)
             (emit-symbol 'bar)
             (emit-symbol 'no-thing))
           @*core-symbols-in-use*)
         '#{bazz foo bar})))

(deftest no-alias-emiting-object-members-tests
  (is (= (binding [*aliases* (ref '{foo fool})]
           (js (. bar foo)))
         "bar.foo()"))
  (is (= (binding [*aliases* (ref '{foo fool})]
           (js (. bar (foo 1 2))))
         "bar.foo(1, 2)"))
  (is (= (binding [*aliases* (ref '{foo fool})]
           (js (foo :bar)))
         "fool('bar')"))
  (is (= (binding [*aliases* (ref '{foo fool})]
           (js (get* foo boo)))
         "fool[boo]")))

(deftest alias-tests
  (is (= (binding [*aliases* (ref '{foo fool})]
           (js (alias boo bar))
           @*aliases*)
         '{foo fool
           boo bar})))

(deftest detect-form-test
  (is (= (detect-form '(foo 1 :x))
         "foo"))
  (is (= (detect-form 123)
         :default))
  (is (= (detect-form "foo")
         :default)))

(deftest sym->property-test
  (is (= (sym->property :a)
         "'a'"))
  (is (= (sym->property 'b)
         "'b'"))
  (is (= (sym->property '.c)
         "'c'")))

(deftest emit-delimted-test
  (is (= (with-out-str (emit-delimited "," [1 2 3]))
         "1,2,3"))
  (is (= (with-out-str
           (emit-delimited ";" [1 2 3]
                           (fn [number] (print (inc number)))))
         "2;3;4")))

(deftest emit-map-test
  (is (= (with-out-str (emit-map {:a 1 :b 2}))
         "{a : 1,b : 2}"))
  (is (= (try+
          (js
           (with-out-str (emit-map {:a 1 "b" {'c 2}})))
          (catch [:known-error true] e
            (:msg e)))
         (str "Error emitting this map `{(quote c) 2}`:\n"
              "Invalid map key: `(quote c)`.\n"
              "Valid keys are elements which can be converted "
              "to strings."))))

(deftest literals
  (is (= (js use-camel-case) "useCamelCase"))
  (is (= (js predicate?)  "predicate_p"))
  (is (= (js *ear-muffs*) "__earMuffs__"))
  (is (= (js special*)    "special__"))
  (is (= (js with-quote') "withQuote_q"))
  (is (= (js has-side-effect!) "hasSideEffect_s"))
  (is (= (js a->b) "aToB"))
  (is (= (js -) "_"))
  (is (= (js /) "_divide"))
  (is (= (js (js-divide 1 2)) "(1 / 2)"))
  (is (= (js :foo?) "'foo_p'"))

  (is (= (js {:foo 1 :bar 2 :baz 3}) "{foo : 1,bar : 2,baz : 3}"))
  (is (= (js #{:foo :bar :baz}) "hashSet('foo', 'bar', 'baz')"))
  (is (= (js [:foo :bar :baz]) "['foo','bar','baz']"))
  (is (= (js \newline) "'\n'"))
  (is (= (js ".") "\".\""))
  (is (= (js "defmacro") "\"defmacro\""))
  (is (= (js ".abc") "\".abc\""))
  (is (= (js \a) "'a'")))

(deftest functions
  (is (= (js (+* 1 2 3)) "(1 + 2 + 3)"))
  (is (= (js (+* "foo" "bar" "baz")) "(\"foo\" + \"bar\" + \"baz\")"))
  (is (= (js (:test {:test 1 :foo 2 :bar 3}))
         "get({test : 1,foo : 2,bar : 3}, 'test')"))
  (is (= (js (append '(:foo bar baz) '(quux)))
         "append(['foo','bar','baz'], ['quux'])"))

  (is (= (js (fn* [a b] (+* a b)))
         "function (a, b) { return (a + b); }"))

  (is (= (js (fn* foo [a b] (+* a b)))
         "function foo (a, b) { return (a + b); }"))

  (is (= (with-pretty-print (js (fn* "Some func does stuff" [x] (+* x 1))))
         (str "function (x) {\n"
              "    /* Some func does stuff */\n"
              "    return (x + 1);\n}")))

  (is (= (with-pretty-print (js (fn* "Some func\ndoes stuff" [x] (+* x 1))))
         (str "function (x) {\n"
              "    /* Some func\n"
              "       does stuff */\n"
              "    return (x + 1);\n}")))

  (is (= (js (fn* foo [a b] (+* a b)))
         "function foo (a, b) { return (a + b); }"))

  (is (= (js (fn* foo [c] (.methodOf c)))
         "function foo (c) { return c.methodOf(); }"))

  (is (= (js
          (fn* test []
            (let [a 1] (log (** a a)))
            (let [a 2] (log (** a a)))))
         (str  "function test () {"
               " (function () {"
               " var a = 1;"
               " return log((a * a));"
               "  })();"
               " return (function () {"
               " var a = 2;"
               " return log((a * a));"
               "  })() }"
               ))))

(deftest function-with-let-inside-tests
  (is (= (js
          (fn* test []
            (let [a 1] (log (** a a)))
            (do (log "test") (+* 1 1))))
         (str "function test () {"
              " (function () {"
              " var a = 1;"
              " return log((a * a));"
              "  })();"
              "  log(\"test\");"
              " return (1 + 1); }"
              ))))

(deftest normalize-dot-form-test
  (is (= (normalize-dot-form '.)
         '.))
  (is (= (normalize-dot-form '.f)
         'f))
  (is (= (normalize-dot-form '.foo)
         'foo))
  (is (= (normalize-dot-form 'Foo.)
         'Foo))
  (is (= (normalize-dot-form 'F.)
         'F)))

(deftest property-access
  (is (= (js (get* map :some-key))
         "map.someKey"))
  (is (= (js (:an-other-key map))
         "get(map, 'anOtherKey')")))

(deftest dot-form-test
  (is (= (js (. foo -bar))
         "foo.bar"))
  (is (= (js (.-bar foo))
         "foo.bar"))
  (is (= (js (. foo bar))
         "foo.bar()"))
  (is (= (js (. foo bar :bazz 0))
         "foo.bar('bazz', 0)"))
  (is (= (js (.bar foo))
         "foo.bar()"))
  (is (= (js (.bar (new foo)))
         "new foo().bar()"))
  (is (= (js (. (. a (b 1)) (c 2)))
         "a.b(1).c(2)")))

(deftest destructuring
  (is (= (js
          (fn* test []
               (let [a 1
                     b (+* a 1)
                     c (+* b 1)]
                 (+* a b c))))
         (str "function test () {"
              " var a = 1, b = (a + 1), c = (b + 1);"
              " return (a + b + c); }")))

  ;; & rest
  (is (= (js
          (fn* test []
               (let [[a b & r] [1 2 3 4]]
                 [(+* a b) r])))
         (str "function test () {"
              " var _temp_1000 = [1,2,3,4],"
              " a = _temp_1000[0],"
              " b = _temp_1000[1],"
              " r = _temp_1000.slice(2);"
              " return [(a + b),r]; }")))

  (is (= (js
          (fn* test [[a b & r]]
               [(+* a b) r]))
         (str "function test () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " _temp_1001 = _temp_1000[0],"
              " a = _temp_1001[0],"
              " b = _temp_1001[1],"
              " r = _temp_1001.slice(2);"
              " return [(a + b),r]; }")))

  (is (= (js
          (fn* test [a b & r]
               [(+* a b) r]))
         (str "function test () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " a = _temp_1000[0],"
              " b = _temp_1000[1],"
              " r = _temp_1000.slice(2);"
              " return [(a + b),r]; }")))

  ;; :as
  (is (= (js
          (fn* [a [b] [c d & e :as f] :as g] nil))
         (str "function () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " a = _temp_1000[0],"
              " _temp_1001 = _temp_1000[1],"
              " b = _temp_1001[0],"
              " _temp_1002 = _temp_1000[2],"
              " c = _temp_1002[0],"
              " d = _temp_1002[1],"
              " e = _temp_1002.slice(2),"
              " f = _temp_1002,"
              " g = _temp_1000; }")
         ))

  ;; map destructuring
  (is (= (js
          (fn* [x {y :y, fred :fred}] fred))
         (str "function () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " x = _temp_1000[0],"
              " _temp_1001 = _temp_1000[1],"
              " y = get(_temp_1001, 'y'),"
              " fred = get(_temp_1001, 'fred');"
              " return fred; }")))

  (is (= (js
          (fn* [[{x :x, {z :z} :y}]] z))
         (str "function () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " _temp_1001 = _temp_1000[0],"
              " _temp_1002 = _temp_1001[0],"
              " x = get(_temp_1002, 'x'),"
              " _temp_1003 = get(_temp_1002, 'y'),"
              " z = get(_temp_1003, 'z');"
              " return z; }")))

  ;; numbers as keys (this actually works)
  (is (= (js
          (fn* [{a 1, b 2, :or {a 3}}]))
         (str "function () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " _temp_1001 = _temp_1000[0],"
              " a = get(_temp_1001, 1, 3),"
              " b = get(_temp_1001, 2); }")
         ))

  ;; :keys, :strs
  (is (= (js
          (fn* [x {y :y, z :z :keys [a b]}] z))
         (str "function () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " x = _temp_1000[0],"
              " _temp_1001 = _temp_1000[1],"
              " a = get(_temp_1001, 'a'),"
              " b = get(_temp_1001, 'b'),"
              " y = get(_temp_1001, 'y'),"
              " z = get(_temp_1001, 'z');"
              " return z; }")))

  (is (= (js
          (fn* [x {y :y, z :z :strs [a b]}] z))
         (str "function () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " x = _temp_1000[0],"
              " _temp_1001 = _temp_1000[1],"
              " a = get(_temp_1001, 'a'),"
              " b = get(_temp_1001, 'b'),"
              " y = get(_temp_1001, 'y'),"
              " z = get(_temp_1001, 'z');"
              " return z; }")))

  (is (= (js
          (fn* [x {y :y, z :z :or {y 1, z "foo"}}] z))
         (str "function () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " x = _temp_1000[0],"
              " _temp_1001 = _temp_1000[1],"
              " y = get(_temp_1001, 'y', 1),"
              " z = get(_temp_1001, 'z', \"foo\");"
              " return z; }")))

  (is (= (js
          (fn* [x {y :y, z :z :keys [a b] :or {a 1, y :bleh}}] z))
         (str "function () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " x = _temp_1000[0],"
              " _temp_1001 = _temp_1000[1],"
              " a = get(_temp_1001, 'a', 1),"
              " b = get(_temp_1001, 'b'),"
              " y = get(_temp_1001, 'y', 'bleh'),"
              " z = get(_temp_1001, 'z');"
              " return z; }")))

  (is (= (js
          (fn* [{x :x y :y :as all}]
               [x y all]))
         (str "function () {"
              " var _temp_1000 = Array.prototype.slice.call(arguments),"
              " all = _temp_1000[0],"
              " x = get(all, 'x'),"
              " y = get(all, 'y');"
              " return [x,y,all]; }")))
  ;; unsupported for now
  (is (= (try+
          (js
           (fn* [x y & {z :z}] z))
          (catch [:known-error true] e
            (:msg e)))
         (str "Unsupported binding form `[x y & {z :z}]`:\n"
              "`&` must be followed by exactly one symbol"))))

(deftest loops
  (is (= (js
          (fn* join [arr delim]
            (loop [str (get* arr 0)
                   i 1]
              (if (< i (count arr))
                (recur (+* str delim (get* arr i))
                       (+* i 1))
                str))))
         (str "function join (arr, delim) {"
              " for (var str = arr[0], i = 1; true;) {"
              " if ((i < count(arr))) {"
              " var _temp_1000 = [(str + delim + arr[i]),(i + 1)];\n"
              " str = _temp_1000[0];"
              " i = _temp_1000[1];"
              " continue; }"
              " else {"
              " return str; };"
              " break; }; }"))))

(deftest inline-if
  (is (= (js
          (fn* test [a]
            ((if (> a 0) minus plus) a 1)))
         "function test (a) { return (((a > 0) ? minus : plus))(a,1); }"))

  (is (= (js (fn* test [a] (console.log (if (> a 0) a))))
         (str "function test (a) {"
              " return console.log("
              "((a > 0) ? a : void(0))); }")
         )))

(deftest if-tests
  (is (= (js (if a b c))
         "if (a) { b; } else { c; }"))
  (is (= (js (if :a b c))
         "if ('a') { b; } else { c; }"))
  (is (= (js (def x (if x :a :b)))
         "var x = (x ? 'a' : 'b')"))
  (is (= (js (def x (if true :a :b)))
         "var x = (true ? 'a' : 'b')"))
  (is (= (js (fn* [] (if a b c)))
         "function () { if (a) { return b; } else { return c; }; }"))
  (is (= (js (fn* [] (if :true b c)))
         "function () { if ('true') { return b; } else { return c; }; }"))
  )

(deftest inline-primitives
  (is (= (js (fn* foo [i c] (inline "i instanceof c")))
         "function foo (i, c) { return i instanceof c; }")))

(deftest case-tests
  (is (= (with-pretty-print (js (case answer 42 (bingo) 24 (tiny))))
         (str "switch (answer) {\n"
              "    case 42:\n"
              "        bingo();\n"
              "        break;\n"
              "    case 24:\n"
              "        tiny();\n"
              "        break;\n"
              "}")))
  (is (= (js (case answer 42 (bingo) 24 (tiny)))
         (str "switch (answer) {"
              " case 42: bingo(); break; case 24: tiny(); break; }")))
  (is (= (js (fn* foo [answer] (case answer 42 (bingo) 24 (tiny))))
         (str "function foo (answer) {"
              " switch (answer) {"
              " case 42: return bingo(); "
              " case 24: return tiny();  }; }")))
  (is (= (with-pretty-print (js (def foo (case answer 42 (bingo) 24 (tiny)))))
         (str "var foo = (function(){\n"
              "    switch (answer) {\n"
              "        case 42:\n"
              "            return bingo();\n"
              "            \n"
              "        case 24:\n"
              "            return tiny();\n"
              "            \n"
              "    }})()")))
  (is (= (js (case answer (+* 10 20) (bingo)))
         "switch (answer) { case (10 + 20): bingo(); break; }"))
  (is (= (js (case answer "text" (foo) (+* 10 20) (bingo)))
         (str "switch (answer) {"
              " case \"text\": foo(); break;"
              " case (10 + 20): bingo(); break; }")))
  (is (= (js (case answer 1 :one 2 :two "anything else"))
         (str "switch (answer) {"
              " case 1: 'one'; break;"
              " case 2: 'two'; break;"
              " default: \"anything else\"; }"))))

(deftest do-test
  (is (= (with-pretty-print
           (binding [*inline-if* true]
             (js (do 1 2 3))))
         (str "(function(){\n"
              "    \n"
              "    1;\n"
              "    2;\n"
              "    return 3;})()")))
  (is (= (binding [*inline-if* false]
           (js (do 1 2 3)))
         " 1; 2; 3;")))

(deftest try-catch-finally
  (is (= (js
          (fn* test []
            (try
              (js-divide 5 0)
              (catch ex
                  (console.log ex))
              (finally
               0))))
         (str "function test () {"
              " try { return (5 / 0); } catch (ex) {"
              " return console.log(ex); }"
              " finally {"
              " return 0; }; }")))

  (is (= (js
          (fn* test [a]
            (if (< a 0) (throw (new Error "Negative numbers not accepted")))))
         (str "function test (a) {"
              " if ((a < 0)) {"
              " throw new Error(\"Negative numbers not accepted\"); }; }"))))

(deftest let-tests
  (is (= (js (def x (let [y 3] y)))
         "var x = (function () { var y = 3; return y;  })()"))
  (is (= (js (fn* [] (let [x 1 y 2] (plus x y))))
         "function () { var x = 1, y = 2; return plus(x, y); }"))
  (is (= (js (fn* [] (let [x 1 y 2] (plus x y)) 3))
         (str "function () {"
              " (function () {"
              " var x = 1, y = 2; return plus(x, y);  })();"
              " return 3; }")))
  (is (= (js (let [m {:test 1 :foo 2 :bar 3}] (list m 4)))
         (str "(function () { var m = {test : 1,foo : 2,bar : 3};"
              " return list(m, 4);  })();"))))

(deftest js-let-test
  (is (= (js-let [a 2 b 3] (+* a b))
         " (function (a, b) { return (a + b); })(2,3);")))

(deftest let-js-test
  (is (= (let-js [foo 1]
                 `(def x ~foo))
         "var x = 1")))

(deftest new-and-delete-tests
  (is (= (js (Foo. :bar))
         "new Foo('bar')"))
  (is (= (js (new bar boo buzz))
         "new bar(boo,buzz)"))
  (is (= (js (delete foo))
         "delete foo")))

(borrow-macros '..)
(deftest expand-macro-test
  (is (= (expand-macro-1 'foo)
         'foo))
  (is (= (expand-macro-1 '(.. foo bar))
         '(. foo bar)))
  (is (= (expand-macro '(.. foo (bar) (buzz)))
         '(. (. foo (bar)) (buzz)))))

(deftest macroexpand-1-test
  (is (= (js (macroexpand-1 (. foo (bar))))
         "\"(. foo (bar))\""))
  (is (= (js (macroexpand-1 (.. foo (bar) (buzz))))
         "\"(.. (. foo (bar)) (buzz))\""))
  (is (= (js (macroexpand (.. foo (bar) (buzz))))
         "\"(. (. foo (bar)) (buzz))\""))
  ;; ensures namespaces are removed
  (is (= (js (macroexpand (some-namespace/str foo (bar) (buzz))))
         "\"(str foo (bar) (buzz))\"")))

(deftest dofor-test
  (is (= (js
          (dofor [(let* i 0
                        j 1)
                  (< i 5)
                  (set! i (+* i 1))]
                 1))
         "for ( var i = 0, j = 1; (i < 5); i = (i + 1);) { 1; }"))
  (is (= (js
          (dofor [(def i 0)
                  (< i 5)
                  (set! i (+* i 1))]
                 1))
         "for ( var i = 0; (i < 5); i = (i + 1);) { 1; }"))
  (is (= (js
          (dofor [[i 0 j 1]
                  (< i 5)
                  (set! i (+* i 1))]
                 1))
         "for ( var i = 0, j = 1; (i < 5); i = (i + 1);) { 1; }")))

(deftest regexp-test
  (is (= (js #"foo")
         "/foo/"))
  (is (= (js #"(?i)foo")
         "/foo/i"))
  (is (= (js #"^([a-z]*)([0-9]*)")
         "/^([a-z]*)([0-9]*)/")))

(deftest do-while-test
  (is (= (js
           (do-while (< x 10)
                     (set! x (+* x 1))))
         "do { x = (x + 1); } while ((x < 10))"
         ))
  (is (= (js
           (do-while (and (< x 10) (> x 5))
                     (set! x (+* x 1))))
         "do { x = (x + 1); } while (((x < 10) && (x > 5)))"
         )))

(deftest require*-test
  (is (= (js
          (require* "foo.js"))
         "require(\"foo.js\")")))
