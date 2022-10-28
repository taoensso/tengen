(ns taoensso.tengen-tests
  (:require
   [clojure.test    :as test :refer [deftest testing is]]
   [taoensso.encore :as enc]
   [taoensso.tengen.common :as common]))

(comment
  (remove-ns      'taoensso.tengen-tests)
  (test/run-tests 'taoensso.tengen-tests))

;;;;

#?(:clj  (deftest _test-clj  (is (= 1 1))))
#?(:cljs (deftest _test-cljs (is (= 1 1))))

;;;;

#?(:cljs
   (defmethod test/report [:cljs.test/default :end-run-tests] [m]
     (when-not (test/successful? m)
       ;; Trigger non-zero `lein test-cljs` exit code for CI
       (throw (ex-info "ClojureScript tests failed" {})))))

#?(:cljs (test/run-tests))
