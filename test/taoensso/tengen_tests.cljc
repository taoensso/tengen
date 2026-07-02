(ns taoensso.tengen-tests
  (:require
   [clojure.test :as test :refer [deftest is]]
   #?(:cljs [cljs.test :refer [async]])
   #?(:cljs [reagent.core :as reagent])
   #?(:cljs [taoensso.tengen.reagent :refer-macros [cmptfn]])))

(comment
  (remove-ns      'taoensso.tengen-tests)
  (test/run-tests 'taoensso.tengen-tests))

;;;;

#?(:cljs
   (defn- set-cmpt-argv! [cmpt argv]
     (let [props (js-obj)]
       (set! (.-argv props) argv)
       (set! (.-props cmpt) props)
       (set! (.-reagentRender cmpt) (fn [] nil))
       cmpt)))

#?(:cljs (defn- new-test-cmpt [argv] (set-cmpt-argv! (js-obj) argv)))

#?(:cljs
   (deftest _reagent-lifecycle
     (let [events_    (atom [])
           class-map_ (atom nil)]

       (with-redefs [reagent/create-class (fn [class-map] (reset! class-map_ class-map) ::component-class)]

         (is (= ::component-class
                (cmptfn ::lifecycle [x]
                  :let-mount [mount-val (do (swap! events_ conj [:let-mount x]) (str "mount-" x))]

                  :let-render
                  [render-val (do (swap! events_ conj [:let-render x this-mounting? mount-val])
                                  (str "render-" x))]

                  :render
                  (do
                    (swap! events_ conj [:render x this-mounting? mount-val render-val])
                    [:div (str x "/" mount-val "/" render-val)])

                  :post-render (swap! events_ conj [:post-render x this-mounting? mount-val render-val])
                  :unmount     (swap! events_ conj [:unmount x mount-val render-val])))))

       (let [class-map @class-map_
             cmpt      (new-test-cmpt [::component "Ada"])]

         (is (= (str ::lifecycle) (:display-name class-map)))

         (is (= [:div "Ada/mount-Ada/render-Ada"] ((:render class-map) cmpt)))
         (is (= [:div "Ada/mount-Ada/render-Ada"] ((:render class-map) cmpt)))

         ((:component-did-mount class-map) cmpt)

         (set-cmpt-argv! cmpt [::component "Grace"])
         (is (= [:div "Grace/mount-Ada/render-Grace"] ((:render class-map) cmpt)))
         ((:component-did-update class-map) cmpt [::component "Ada"])

         (is (true? (.-cfnMountRvalsSet cmpt)))
         (is (= [["mount-Ada"] ["render-Grace"]]
                [(.-cfnMountRvals cmpt) (.-cfnRenderRvals cmpt)]))

         ((:component-will-unmount class-map) cmpt)

         (is (some? (.-cfnClearRvalsTimeout cmpt)))
         (is (= [true ["mount-Ada"] true ["render-Grace"]]
                [(.-cfnMounted cmpt)
                 (.-cfnMountRvals cmpt)
                 (.-cfnMountRvalsSet cmpt)
                 (.-cfnRenderRvals cmpt)]))

         ((:component-did-mount class-map) cmpt)

         (is (nil? (.-cfnClearRvalsTimeout cmpt)))
         (is (= [true ["mount-Ada"] true ["render-Grace"]]
                [(.-cfnMounted cmpt)
                 (.-cfnMountRvals cmpt)
                 (.-cfnMountRvalsSet cmpt)
                 (.-cfnRenderRvals cmpt)]))

         (is (= [[:let-mount "Ada"]
                 [:let-render "Ada" true "mount-Ada"]
                 [:render "Ada" true "mount-Ada" "render-Ada"]
                 [:let-render "Ada" true "mount-Ada"]
                 [:render "Ada" true "mount-Ada" "render-Ada"]
                 [:post-render "Ada" true "mount-Ada" "render-Ada"]
                 [:let-render "Grace" false "mount-Ada"]
                 [:render "Grace" false "mount-Ada" "render-Grace"]
                 [:post-render "Grace" false "mount-Ada" "render-Grace"]
                 [:unmount "Grace" "mount-Ada" "render-Grace"]
                 [:post-render "Grace" true "mount-Ada" "render-Grace"]]
                @events_))))))

#?(:cljs
   (deftest _reagent-unmount-clears-lifecycle-state
     (async done
       (let [class-map_ (atom nil)]

         (with-redefs [reagent/create-class (fn [class-map] (reset! class-map_ class-map) ::component-class)]
           (cmptfn ::unmount-clear [x]
             :let-mount  [mount-val (str "mount-" x)]
             :let-render [render-val (str "render-" x)]
             :render     [:div (str x "/" mount-val "/" render-val)]
             :unmount    nil))

         (let [class-map @class-map_
               cmpt      (new-test-cmpt [::component "Ada"])]
           ((:render class-map) cmpt)
           ((:component-did-mount class-map) cmpt)
           ((:component-will-unmount class-map) cmpt)

           (js/setTimeout
             (fn []
               (is (= [nil nil nil nil nil]
                      [(.-cfnMounted cmpt)
                       (.-cfnMountRvals cmpt)
                       (.-cfnMountRvalsSet cmpt)
                       (.-cfnRenderRvals cmpt)
                       (.-cfnClearRvalsTimeout cmpt)]))
               (done))
             10))))))

#?(:cljs
   (deftest _reagent-error-wrapping
     (let [class-map_ (atom nil)]

       (with-redefs [reagent/create-class (fn [class-map] (reset! class-map_ class-map) ::component-class)]
         (cmptfn ::error [x] :render (throw "boom")))

       (try
         ((:render @class-map_) (new-test-cmpt [::component "Ada"]))
         (is false "Expected wrapped render error")
         (catch :default e
           (let [data (ex-data e)]
             (is (= ::error (:id data)))
             (is (= (str ::error) (:path data)))
             (is (= :render (:lifecycle-id data)))
             (is (= "boom" (:thrown data)))
             (is (true? (:mounting? data)))))))))

;;;;

#?(:cljs
   (defmethod test/report [:cljs.test/default :end-run-tests] [m]
     (when-not (test/successful? m)
       ;; Trigger non-zero `lein test-cljs` exit code for CI
       (throw (ex-info "ClojureScript tests failed" {})))))

#?(:cljs (test/run-tests))
