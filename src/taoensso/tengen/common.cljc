(ns taoensso.tengen.common
  "Private common implementation details."
  (:require
   [taoensso.encore :as enc :refer [have have?]]))

(enc/assert-min-encore-version [2 85 0])

#?(:clj
   (defmacro binding-rvals
     "Evaluates and returns vector of rhs values for given bindings while
     preserving support for the usual let-binding facilities like destructuring,
     referring to previous bindings, etc.

       [{:keys [x]} {:x 1}
        <...>
       ] ->
       (let [alias1      {:x 1}
             {:keys [x]} alias1
             <...>
            ]
         [alias1 <...>])"

     [bindings]
     (let [pairs   (partition 2 bindings)
           lvals   (mapv first  pairs)
           rvals   (mapv second pairs)
           aliases (mapv (fn [i] (symbol (str "__lv" i))) (range (count lvals)))

           alias-bindings (interleave aliases rvals)
           lval-bindings  (interleave lvals   aliases)

           alias-bpairs (partition 2 alias-bindings) ; [(<alias>  <rval>) ...]
           lval-bpairs  (partition 2 lval-bindings)  ; [(<lval>  <alias>) ...]

           bindings* (reduce into [] (interleave alias-bpairs lval-bpairs))]

       `(let ~bindings* ~aliases))))

(comment
  (do             (binding-rvals [x 1, {:keys [a b]} {:a x :b x}]))
  (macroexpand-1 '(binding-rvals [x 1, {:keys [a b]} {:a x :b x}]))
  (macroexpand   '(binding-rvals [x 1, {:keys [a b]} {:a x :b x}])))

(defn split-let-pairs [bindings]
  (if (seq bindings)
    (let [parts (partition 2 bindings)]
      [(mapv first parts) (mapv second parts)])
    ['__ #_[] nil]))

(comment
  (split-let-pairs [:a :b :c :d])
  (split-let-pairs nil))

(defn hash-map-with-unique-ks [kvs]
  (enc/reduce-kvs
    (fn [acc k v]
      (if (contains? acc k)
        (throw
          (ex-info "Duplicate map key"
            {:k k, :old-v (get acc k), :new-v v}))
        (assoc acc k v)))
    {}
    kvs))

(comment (hash-map-with-unique-ks [:a :A :b :B :a :A2]))

#?(:clj
   (defmacro cmptfn [impl-constructor-fn id params & args]
     (let [implicit-render-body? (odd? (count args))
           args-map
           (if implicit-render-body?
             (hash-map-with-unique-ks (butlast args))
             (hash-map-with-unique-ks          args))

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
           [ mount-lvals  _mount-rvals] (split-let-pairs  mount-bindings)
           [render-lvals _render-rvals] (split-let-pairs render-bindings)

           ;; Define our cfn lifecycle fns
           ;; NB We try minimize code expansion size here (esp. gensyms)

           argv
           (if (seq params)
             (into ['__] params) ; [__ x y z ...]
             '__)

           ?mount-rvals-fn
           (when (seq mount-bindings)
             `(fn [~'this-cmpt ~argv]
                (binding-rvals ~mount-bindings)))

           ?render-rvals-fn
           (when (seq render-bindings)
             `(fn [~'this-cmpt ~argv ~'this-mounting? ~mount-lvals]
                (binding-rvals ~render-bindings)))

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

       `(~impl-constructor-fn
         ~id
         ~?mount-rvals-fn
         ~?render-rvals-fn
         ~render-fn
         ~?post-render-fn
         ~?unmount-fn))))

#?(:clj
   (defmacro def-cmptfn [impl-constructor-fn sym id & sigs]
     (let [[sym args] (enc/name-with-attrs sym sigs)]
       `(def ~sym
          (cmptfn
            ~impl-constructor-fn
            ~id ; ~(str *ns* "/" sym ":" (:line (meta &form) "?"))
            ~@args)))))
