(ns taoensso.tengen-hooks
  (:require
   [clj-kondo.hooks-api :as hooks]))

(defn- do-node [body]
  (hooks/list-node
    (into [(hooks/token-node 'do)] body)))

(defn- keyword-node? [node]
  (keyword? (hooks/sexpr node)))

(defn- parse-cmptfn-args [args]
  (let [implicit-render? (odd? (count args))
        render-node      (when implicit-render? (last args))
        kvs              (if implicit-render? (butlast args) args)]
    (reduce
      (fn [m [k v]]
        (if (keyword-node? k)
          (assoc m (hooks/sexpr k) v)
          m))
      (cond-> {}
        render-node (assoc :render render-node))
      (partition 2 kvs))))

(defn- cmptfn-body-node [args]
  (let [{:keys [let-mount let-render render post-render unmount catch]}
        (parse-cmptfn-args args)

        let-mount  (or let-mount  (hooks/vector-node []))
        let-render (or let-render (hooks/vector-node []))
        body       (cond-> []
                     render      (conj render)
                     post-render (conj post-render)
                     unmount     (conj unmount))
        catch-body (when catch
                     (hooks/list-node
                       [(hooks/token-node 'let)
                        (hooks/vector-node
                          [(hooks/token-node 'this-error) (hooks/token-node nil)])
                        catch]))]

    (hooks/list-node
      [(hooks/token-node 'let)
       (hooks/vector-node
         [(hooks/token-node 'this-cmpt)      (hooks/token-node nil)
          (hooks/token-node 'this-mounting?) (hooks/token-node nil)])

       (do-node
         [(hooks/token-node 'this-cmpt)
          (hooks/token-node 'this-mounting?)
          (hooks/list-node
            [(hooks/token-node 'let)
             let-mount
             (hooks/list-node
               [(hooks/token-node 'let)
                let-render
                (do-node (cond-> body catch-body (conj catch-body)))])])])])))

(defn cmptfn [{:keys [node]}]
  (let [[_macro _id params & args] (:children node)]
    {:node
     (hooks/list-node
       [(hooks/token-node 'fn)
        params
        (cmptfn-body-node args)])}))

(defn def-cmptfn [{:keys [node]}]
  (let [[_macro sym & args] (:children node)
        [doc args]   (if (hooks/string-node? (first args)) [(first args) (next args)] [nil args])
        [attr args]  (if (hooks/map-node?    (first args)) [(first args) (next args)] [nil args])
        params       (first args)
        cmptfn-args  (next args)]
    {:node
     (hooks/list-node
       (cond-> [(hooks/token-node 'defn) sym]
         doc  (conj doc)
         attr (conj attr)
         true (conj params (cmptfn-body-node cmptfn-args))))}))
