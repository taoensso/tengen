(ns taoensso.tengen.reagent
  "Public API for use with Reagent,
  Ref. https://github.com/reagent-project/reagent."
  {:author "Peter Taoussanis (@ptaoussanis)"}

  #?(:clj
     (:require
      [taoensso.encore        :as enc :refer [have have?]]
      [taoensso.tengen.common :as common]))

  #?(:cljs
     (:require
      [taoensso.encore        :as enc    :refer-macros [have have?]]
      [taoensso.tengen.common :as common :refer-macros []]
      [reagent.core])))

(comment
  (enc/declare-remote
    reagent.core/create-class
    reagent.core/argv))

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
         ;; Invoked (only once per instance) before 1st rendering
         :component-will-mount
         (when-let [f ?mount-rvals-fn]
           (fn [cmpt]
             (let [argv  (reagent.core/argv cmpt)
                   rvals (f cmpt argv)]
               (set! (.-cfnMountRvals cmpt) rvals))))

         :render
         (fn [cmpt]
           (let [argv        (reagent.core/argv cmpt)
                 mounting?   (not (.-cfnMounted cmpt))
                 mount-rvals (.-cfnMountRvals   cmpt)]

             (try
               (let [render-rvals
                     (when-let [f ?render-rvals-fn]
                       (let [rvals (f cmpt argv mounting? mount-rvals)]
                         (set! (.-cfnRenderRvals cmpt) rvals)
                         rvals))]

                 (render-fn cmpt argv mounting? mount-rvals render-rvals))

               (catch js/Error e
                 (throw
                   (ex-info "Render error"
                     {:id id
                      :path (reagent.core/component-path cmpt)
                      :mounting? mounting?}
                     e))))))

         ;; Invoked (only once per instance) after 1st rendering
         :component-did-mount
         (fn [cmpt]
           (set! (.-cfnMounted cmpt) true)
           (when-let [f ?post-render-fn]
             (let [argv (reagent.core/argv cmpt)
                   mounting? true]
               (f cmpt argv mounting?
                 (.-cfnMountRvals  cmpt)
                 (.-cfnRenderRvals cmpt)))))

         ;; Invoked before every rendering but the first
         ;; :component-will-update nil #_ (fn [cmpt new-argv])

         ;; Invoked after every rendering but the first
         :component-did-update
         (when-let [f ?post-render-fn]
           (fn [cmpt old-argv]
             (let [argv (reagent.core/argv cmpt)
                   mounting? false]
               (f cmpt argv mounting?
                 (.-cfnMountRvals  cmpt)
                 (.-cfnRenderRvals cmpt)))))

         :component-will-unmount
         (when-let [f ?unmount-fn]
           (fn [cmpt]
             (let [argv (reagent.core/argv cmpt)]
               (f cmpt argv
                 (.-cfnMountRvals  cmpt)
                 (.-cfnRenderRvals cmpt)))))))))

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
    * (cmptfn <...>) - to get inlining.
    * [cmptfn <...>] - to get an intermediary Reagent component:
      * Rerender-iff-args-change semantics.
      * Can take ^{:key _} [cmptfn <...>]."
  [id params & args]
  (let [implicit-render-body? (odd? (count args))
        args-map
        (if implicit-render-body?
          (common/hash-map-with-unique-ks (butlast args))
          (common/hash-map-with-unique-ks          args))

        _ (have? [:ks<= #{:let-mount :let-render :render
                          :post-render :unmount}]
            args-map)

        _ (when implicit-render-body?
            (assert (not (contains? args-map :render))
              "Ambiguous render body: provided as both a :render value and implicit final arg"))

        have-arg?   (set (keys args-map)) ; For existance check w/o val eval
        render-body (if implicit-render-body? (last args) (:render args-map))
        _           (assert render-body "No (nil) render body provided")

        ;; [x :x y x]
        mount-bindings  (:let-mount  args-map)
        render-bindings (:let-render args-map)

        ;; [[x y] [:x x]] ; We actually just want the lval forms here
        [ mount-lvals  _mount-rvals] (common/split-let-pairs  mount-bindings)
        [render-lvals _render-rvals] (common/split-let-pairs render-bindings)

        ;; Define our cfn lifecycle fns
        ;; NB We try minimize code expansion size here (esp. gensyms)

        argv
        (if (seq params)
          (into ['__] params) ; [__ x y z ...]
          '__)

        ?mount-rvals-fn
        (when (seq mount-bindings)
          `(fn [~'this-cmpt ~argv]
             (common/binding-rvals ~mount-bindings)))

        ?render-rvals-fn
        (when (seq render-bindings)
          `(fn [~'this-cmpt ~argv ~'this-mounting? ~mount-lvals]
             (common/binding-rvals ~render-bindings)))

        render-fn
        `(fn [~'this-cmpt ~argv ~'this-mounting? ~mount-lvals ~render-lvals]
           ~render-body)

        ?post-render-fn
        (when (have-arg? :post-render)
          `(fn [~'this-cmpt ~argv ~'this-mounting? ~mount-lvals ~render-lvals]
             ~(:post-render args-map)))

        ?unmount-fn
        (when (have-arg? :unmount)
          `(fn [~'this-cmpt ~argv ~mount-lvals ~render-lvals]
             ~(:unmount args-map)))]

    `(-new-cmptfn
       ~id
       ~?mount-rvals-fn
       ~?render-rvals-fn
       ~render-fn
       ~?post-render-fn
       ~?unmount-fn)))

(defmacro def-cmptfn
  "Defines a top-level Reagent component fn using `cmptfn`.
  See the `cmptfn` docstring for more details on args, etc."
  [sym & sigs]
  (let [[sym args] (enc/name-with-attrs sym sigs)]
    `(def ~sym
       (cmptfn
         ~(str *ns* "/" sym ":" (:line (meta &form) "?"))
         ~@args))))

(comment
  (macroexpand                  '(cmptfn :id [x] [:div x]))
  (clojure.walk/macroexpand-all '(cmptfn :id [x] [:div x]))
  (clojure.walk/macroexpand-all '(def-cmptfn foo [x] [:div x])))
