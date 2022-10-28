(ns taoensso.tengen.reagent
  "Public API for use with Reagent,
  Ref. https://github.com/reagent-project/reagent."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [taoensso.encore        :as enc :refer [have have?]]
   [taoensso.tengen.common :as common]

   #?(:cljs [reagent.core])
   #?(:cljs [reagent.dom])))

(comment
  (enc/declare-remote
    reagent.core/create-class
    reagent.core/argv))

#?(:cljs
   (defn- throw-cmptfn-error [cause lifecycle-id cmpt-id cmpt mounting?]
     (throw
       (ex-info
         (str "Cmpfn error (" lifecycle-id ") in: " cmpt-id)
         {:id cmpt-id
          :path (enc/oget cmpt "displayName")
          :mounting? mounting?
          :lifecycle-id lifecycle-id}
         cause))))

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
         :render
         (fn [cmpt]
           (let [argv      (reagent.core/argv cmpt)
                 mounting? (not (.-cfnMounted cmpt))
                 mount-rvals
                 (if mounting?
                   ;; Executes (only once per instance) before 1st render body
                   ;; Equivalent to (old, deprecated) :component-will-mount
                   (when-let [f ?mount-rvals-fn]
                     (let [rvals
                           (try
                             (f cmpt argv)
                             (catch js/Error e
                               (throw-cmptfn-error e :let-mount id cmpt mounting?)))]

                       (set! (.-cfnMountRvals cmpt) rvals)
                       (do                          rvals)))

                   (.-cfnMountRvals cmpt))]

             (let [render-rvals
                   (when-let [f ?render-rvals-fn]
                     (let [rvals
                           (try
                             (f cmpt argv mounting? mount-rvals)
                             (catch js/Error e
                               (throw-cmptfn-error e :let-render id cmpt mounting?)))]

                       (set! (.-cfnRenderRvals cmpt) rvals)
                       (do                           rvals)))]

               (try
                 (render-fn cmpt argv mounting? mount-rvals render-rvals)
                 (catch js/Error e
                   (throw-cmptfn-error e :render id cmpt mounting?))))))

         ;; Invoked (only once per instance) after 1st rendering
         :component-did-mount
         (fn [cmpt]
           (set! (.-cfnMounted cmpt) true)
           (when-let [f ?post-render-fn]
             (let [argv (reagent.core/argv cmpt)
                   mounting? true]

               (try
                 (f cmpt argv mounting?
                   (.-cfnMountRvals  cmpt)
                   (.-cfnRenderRvals cmpt))

                 (catch js/Error e
                   (throw-cmptfn-error e :post-render id cmpt mounting?))))))

         ;; Invoked after every rendering but the first
         :component-did-update
         (when-let [f ?post-render-fn]
           (fn [cmpt old-argv]
             (let [argv (reagent.core/argv cmpt)
                   mounting? false]

               (try
                 (f cmpt argv mounting?
                   (.-cfnMountRvals  cmpt)
                   (.-cfnRenderRvals cmpt))

                 (catch js/Error e
                   (throw-cmptfn-error e :post-render id cmpt mounting?))))))

         :component-will-unmount
         (when-let [f ?unmount-fn]
           (fn [cmpt]
             (let [argv (reagent.core/argv cmpt)]

               (try
                 (f cmpt argv
                   (.-cfnMountRvals  cmpt)
                   (.-cfnRenderRvals cmpt))

                 (catch js/Error e
                   (throw-cmptfn-error e :unmount id cmpt false))))))))))

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
         * Can take ^{:key _} [<cmpt> <...>]."
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
