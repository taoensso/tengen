<a href="https://www.taoensso.com" title="More stuff by @ptaoussanis at www.taoensso.com">
<img src="https://www.taoensso.com/taoensso-open-source.png" alt="Taoensso open-source" width="400"/></a>

**[CHANGELOG]** | [API] | current [Break Version]:

```clojure
[com.taoensso/tengen "1.0.0-RC1"]
```

> Please consider helping to [support my continued open-source Clojure/Script work]? 
> 
> Even small contributions can add up + make a big difference to help sustain my time writing, maintaining, and supporting Tengen and other Clojure/Script libraries. **Thank you!**
>
> \- Peter Taoussanis

# Tengen

### Simple let-based [Reagent][] component fns for Clojure/Script

> **Ten-gen** (天元) is a Japanese [Go][] term for the central, and only unique point on the Go board.

[Reactjs][] has its pros and cons. Overall, it can be a good fit for web/native application development with Clojure/Script.

But while React's [lifecycle methods] are flexible, using them correctly can be a little unintuitive.

One of the sharper edges I've found in practice, is the difficulty of **managing simple state flow** through the component lifecycle process. This can actually be harder to do in Clojure/Script than vanilla JS since Clojure intentionally discourages the kind of disposable mutable state that could be handy here.

Net result: one sees a lot of weird contortions using atoms and core.async channels just to get the basic kind of data flow that you'll routinely need in a real application.

As an alternative: **Tengen** gives you a small, simple, lightweight **component constructor** that uses the unique capabilities of Lisp macros to get this:

```clojure
(def-cmptfn my-example-component
  "Optional docstring"
  [first-name last-name] ; Args given to component (will rerender on changes)

  :let-mount ; Optional bindings established on each mount, available downstream
  [norm-fn (fn [s] (str/upper-case (str s)))
   _ (do ) ; Any side-effects on mount (fetch data from server, etc.)
   ]

  :let-render ; Optional bindings established on each render, available downstream
  [norm-first-name (norm-fm first-name)
   norm-last-name  (norm-fn last-name)

   ;;; We also have access to two magic symbols:
   currently-mounting? this-mounting? ; Magic `this-mounting?` binding
   current-cmpt        this-cmpt      ; Magic `this-cmpt` binding
  ]

  :render ; Have all above bindings
  [:div "Full name is: "
   (str norm-first-name " " norm-last-name)]

  :post-render (do) ; Optional: modify state atoms, etc. Have all above bindings.
  :unmount     (do) ; Optional: any cleanup jobs, etc.   Have all above bindings.
  )
```

That is:

 * `:let-mount` and `:let-render` bindings automatically flow down through all later lifecycle stages.
 * Magic `this-mounting?` and `this-cmpt` bindings are automatically available through all lifecycle stages.

These two small features can help cut out a _lot_ of unnecessary complexity when writing real applications. In particular, you'll almost never need to touch or even be aware of the underlying React lifecycle methods.

## Quickstart

Add the necessary dependency to your project:

```clojure
[com.taoensso/tengen "1.0.0-RC1"]
```

And setup your namespace imports:

```clojure
(ns my-cljs-ns
  (:require [taoensso.tengen.reagent :as tengen :refer-macros [cmptfn def-cmptfn]]))
```

And you're good to go, you've already seen the entire API!

Check the `cmptfn`, `def-cmptfn` docstrings for more info.

## FAQ

#### Why only Reagent support?

Just the most familiar with Reagent, so started there. Haven't had time yet to look at extending to other libs, but should be trivial if there's demand (please ping to let me know).

I'll note that [Rum][]'s design in particular looks quite pleasant.

#### How's the performance?

Tengen doesn't add any detectable overhead to your components, it's just a lightweight macro wrapper to Reagent's usual constructor.

#### How does this affect my reactive atoms, etc.?

It doesn't, you can continue to use whatever higher-level state management strategies you prefer.

#### How to access DOM nodes?

As usual for Reagent, use [ref callbacks][]:

```clojure
(def-cmptfn my-example-component [arg1 arg2]
  [:div
   {:ref
    (fn [node]
      (when node
        ;; node is mounted in DOM
        ))}])
```

You can also use `(reagent.core/dom-node this-cmpt)`, etc. - but would **strongly** recommend preferring ref callbacks in general since they're a lot more reliable and React's `findDOMNode` method is expected to become deprecated soon.

## Contacting me / contributions

Please use the project's [GitHub issues page] for all questions, ideas, etc. **Pull requests welcome**. See the project's [GitHub contributors page] for a list of contributors.

Otherwise, you can reach me at [Taoensso.com]. Happy hacking!

\- [Peter Taoussanis]

## License

Distributed under the [EPL v1.0] \(same as Clojure).  
Copyright &copy; 2016 [Peter Taoussanis].

<!--- Standard links -->
[Taoensso.com]: https://www.taoensso.com
[Peter Taoussanis]: https://www.taoensso.com
[@ptaoussanis]: https://www.taoensso.com
[More by @ptaoussanis]: https://www.taoensso.com
[Break Version]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md
[support my continued open-source Clojure/Script work]: http://taoensso.com/clojure/backers

<!--- Standard links (repo specific) -->
[CHANGELOG]: https://github.com/ptaoussanis/tengen/releases
[API]: http://ptaoussanis.github.io/tengen/
[GitHub issues page]: https://github.com/ptaoussanis/tengen/issues
[GitHub contributors page]: https://github.com/ptaoussanis/tengen/graphs/contributors
[EPL v1.0]: https://raw.githubusercontent.com/ptaoussanis/tengen/master/LICENSE
[Hero]: https://raw.githubusercontent.com/ptaoussanis/tengen/master/hero.png "Title"

<!--- Unique links -->
[Go]: https://en.wikipedia.org/wiki/Go_game
[lifecycle methods]: https://facebook.github.io/react/docs/component-specs.html
[Reactjs]: https://facebook.github.io/react/
[Reagent]: https://github.com/reagent-project/reagent
[Rum]: https://github.com/tonsky/rum
[Om]: https://github.com/omcljs/om
[ref callbacks]: https://facebook.github.io/react/docs/more-about-refs.html#the-ref-callback-attribute