(ns taoensso.tengen.reagent
  "Public API for use with Reagent,
  Ref. https://github.com/reagent-project/reagent."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [taoensso.encore        :as enc]
   [taoensso.tengen.common :as common]

   #?(:cljs [reagent.core])))

(comment
  (enc/declare-remote
    reagent.core/create-class
    reagent.core/argv))

#?(:cljs
   (defn- throw-cmptfn-error [cause lifecycle-id cmpt-id ^js cmpt mounting?]
     (let [error-cause (when (instance? js/Error cause) cause)]
       (throw
         (ex-info
           (str "Cmpfn error (" lifecycle-id ") in: " cmpt-id)
           (enc/assoc-some
             {:id cmpt-id
              :path
              (or (enc/oget-in cmpt ["constructor" "displayName"])
                (enc/oget cmpt "displayName")
                (str cmpt-id))
              :mounting? mounting?
              :lifecycle-id lifecycle-id}
             :thrown (when-not error-cause cause))
           error-cause)))))

#?(:cljs
   (defn- cancel-cmptfn-rvals-clear! [^js cmpt]
     (when-let [tid (.-cfnClearRvalsTimeout cmpt)]
       (js/clearTimeout tid)
       (set! (.-cfnClearRvalsTimeout cmpt) nil))
     nil))

#?(:cljs
   (defn- clear-cmptfn-rvals! [^js cmpt]
     (cancel-cmptfn-rvals-clear! cmpt)
     (set! (.-cfnMounted        cmpt) nil)
     (set! (.-cfnMountRvals     cmpt) nil)
     (set! (.-cfnMountRvalsSet  cmpt) nil)
     (set! (.-cfnRenderRvals    cmpt) nil)
     nil))

#?(:cljs
   (defn- schedule-cmptfn-rvals-clear! [^js cmpt]
     (cancel-cmptfn-rvals-clear! cmpt)
     (set! (.-cfnClearRvalsTimeout cmpt)
       (js/setTimeout
         (fn []
           (set! (.-cfnClearRvalsTimeout cmpt) nil)
           (clear-cmptfn-rvals! cmpt))
         0))
     nil))

#?(:cljs
   (defn -new-cmptfn
     "Returns a new Reagent component defined in terms of special cmptfn
     lifecycle fns. These in turn are defined in a special macro context to
     get magic bindings and flow-through behaviour, etc."

     [id               ; For debugging, error reporting, etc.
      ?mount-rvals-fn  ; (fn [cmpt argv])
      ?render-rvals-fn ; (fn [cmpt argv mounting? mount-rvals]), ideally pure
      render-fn        ; (fn [cmpt argv mounting? mount-rvals render-rvals])
      ?post-render-fn  ; (fn [cmpt argv mounting? mount-rvals render-rvals])
      ?unmount-fn      ; (fn [cmpt argv           mount-rvals render-rvals])
      ]

     (reagent.core/create-class
       (enc/assoc-some {}
         :display-name (str id)

         :render
         (fn [^js cmpt]
           (let [argv      (reagent.core/argv cmpt)
                 mounting? (not (.-cfnMounted cmpt))
                 mount-rvals
                 (if mounting?
                   ;; Executes once per instance before 1st render body
                   ;; Equivalent to (old, deprecated) :component-will-mount
                   (if (.-cfnMountRvalsSet cmpt)
                     (.-cfnMountRvals cmpt)
                     (when-let [f ?mount-rvals-fn]
                       (let [rvals
                             (try
                               (f cmpt argv)
                               (catch :default e
                                 (throw-cmptfn-error e :let-mount id cmpt mounting?)))]

                         (set! (.-cfnMountRvals    cmpt) rvals)
                         (set! (.-cfnMountRvalsSet cmpt) true)
                         (do                             rvals))))

                   (.-cfnMountRvals cmpt))]

             (let [render-rvals
                   (when-let [f ?render-rvals-fn]
                     (let [rvals
                           (try
                             (f cmpt argv mounting? mount-rvals)
                             (catch :default e
                               (throw-cmptfn-error e :let-render id cmpt mounting?)))]

                       (set! (.-cfnRenderRvals cmpt) rvals)
                       (do                           rvals)))]

               (try
                 (render-fn cmpt argv mounting? mount-rvals render-rvals)
                 (catch :default e
                   (throw-cmptfn-error e :render id cmpt mounting?))))))

         ;; Invoked (only once per instance) after 1st rendering
         :component-did-mount
         (fn [^js cmpt]
           (cancel-cmptfn-rvals-clear! cmpt)
           (set! (.-cfnMounted cmpt) true)
           (when-let [f ?post-render-fn]
             (let [argv (reagent.core/argv cmpt)
                   mounting? true]

               (try
                 (f cmpt argv mounting?
                   (.-cfnMountRvals  cmpt)
                   (.-cfnRenderRvals cmpt))

                 (catch :default e
                   (throw-cmptfn-error e :post-render id cmpt mounting?))))))

         ;; Invoked after every rendering but the first
         :component-did-update
         (when-let [f ?post-render-fn]
           (fn [^js cmpt old-argv]
             (let [argv (reagent.core/argv cmpt)
                   mounting? false]

               (try
                 (f cmpt argv mounting?
                   (.-cfnMountRvals  cmpt)
                   (.-cfnRenderRvals cmpt))

                 (catch :default e
                   (throw-cmptfn-error e :post-render id cmpt mounting?))))))

         :component-will-unmount
         (fn [^js cmpt]
           (try
             (when-let [f ?unmount-fn]
               (let [argv (reagent.core/argv cmpt)]

                 (try
                   (f cmpt argv
                     (.-cfnMountRvals  cmpt)
                     (.-cfnRenderRvals cmpt))

                   (catch :default e
                     (throw-cmptfn-error e :unmount id cmpt false)))))
             (finally
               (schedule-cmptfn-rvals-clear! cmpt))))))))

#?(:clj
   (defmacro cmptfn
     "Reagent component fn util. Provides a sensible let-flow API for writing
     simple, flexible Clj/s Reagent components.

     (cmptfn
       id              ; Component identifier for debugging, error reporting, etc.
       [x y]           ; Reagent args passed to component
       :let-mount  []  ; Est. with each instance mount,  avail. downstream
       :let-render []  ; Est. with each instance render, avail. downstream, pure!
       :render      <body> ; Or just provide render body as final arg, pure!
       :post-render <body> ; For DOM node setup/mutation
       :unmount     <body> ; For DOM node teardown
     )

     - Magic bindings: `this-cmpt`, `this-mounting?`.
     - Nodes: `[<cmpt> {:ref (fn [node] _)}]` or `(enc/oget ev \"currentTarget\")`.
     - Call Reagent components as:
       * (<cmpt> <...>) - to get inlining.
       * [<cmpt> <...>] - to get an intermediary Reagent component:
         * Rerender-iff-args-change semantics.
         * Can take ^{:key _} [<cmpt> <...>].

     - Lifecycle behaviour:
       * Component `id` becomes the React display-name for DevTools/stack traces.
       * `:let-mount` runs during render; keep it cheap/idempotent.
       * `:let-mount` bindings evaluate once per mount and are cleared after unmount.
       * React StrictMode remounts preserve lifecycle values for the reused instance.
       * StrictMode remounts may run `:post-render` with `this-mounting?` true
         while preserving `:let-render` values from the prior render.
       * Lifecycle errors include component/lifecycle ex-data; non-`js/Error`
         throws are preserved under `:thrown`."
     [id params & args]
     `(common/cmptfn taoensso.tengen.reagent/-new-cmptfn
        ~id ~params ~@args)))

#?(:clj
   (defmacro def-cmptfn
     "Defines a top-level Reagent component fn using `cmptfn`.
     See the `cmptfn` docstring for more details on args, etc."
     [sym & sigs]
     `(common/def-cmptfn taoensso.tengen.reagent/-new-cmptfn
        ~sym
        ~(str *ns* "/" sym ":" (:line (meta &form) "?"))
        ~@sigs)))

(comment
  (macroexpand                  '(cmptfn :id [x] [:div x]))
  (clojure.walk/macroexpand-all '(cmptfn :id [x] [:div x]))
  (clojure.walk/macroexpand-all '(def-cmptfn foo [x] [:div x])))
