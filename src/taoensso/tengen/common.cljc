(ns taoensso.tengen.common
  "Private common implementation details."
  (:require [taoensso.encore :as enc]))

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

    `(let ~bindings* ~aliases)))

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
