<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a><br/>
[**API**][cljdoc] | [Support][GitHub issues] | Latest release: [on Clojars][Clojars URL]

[![Clojars project][Clojars SVG]][Clojars URL]
[![Clj tests][Clj tests SVG]][Clj tests URL]
[![Cljs tests][Cljs tests SVG]][Cljs tests URL]
[![Graal tests][Graal tests SVG]][Graal tests URL]

# Tengen

### Let-based [Reagent][] components for Clojure/Script

> **Ten-gen** (天元) is a Japanese [Go][] term for the central, and only unique point on the Go board.

Tengen is a small, lightweight component constructor that uses Lisp macros to make simple state flow through the React/Reagent component lifecycle easier to manage.

[React][] can be a good fit for web/native application development with Clojure/Script, but its [lifecycle methods][] can be unintuitive. In practice, it is easy to end up with atoms and core.async channels just to move simple values between mount, render, post-render, and unmount logic.

Tengen gives you this:

```clojure
(def-cmptfn my-example-component
  "Optional docstring"
  [first-name last-name] ; Args given to component (will rerender on changes)

  :let-mount ; Optional bindings established on each mount, available downstream
  [norm-fn (fn [s] (str/upper-case (str s)))
   node_   (atom nil)] ; Cheap/idempotent render-phase setup

  :let-render ; Optional bindings established on each render, available downstream
  [norm-first-name (norm-fn first-name)
   norm-last-name  (norm-fn last-name)

   ;; Magic bindings
   currently-mounting? this-mounting?
   current-cmpt        this-cmpt]

  :render ; Has all above bindings
  [:div "Full name is: "
   (str norm-first-name " " norm-last-name)]

  :post-render (do) ; Optional: modify state atoms, etc. Has all above bindings
  :unmount     (do) ; Optional: cleanup jobs, etc. Has all above bindings
  )
```

That is:

- `:let-mount` and `:let-render` bindings automatically flow down through all later lifecycle stages
- Magic `this-mounting?` and `this-cmpt` bindings are automatically available through all lifecycle stages

These two small features can cut out a lot of unnecessary complexity when writing real applications. In particular, you will rarely need to touch or even be aware of the underlying React lifecycle methods.

Note that `:let-mount` runs during React's render phase. Keep it cheap and idempotent: atoms, functions, and derived values are appropriate. DOM setup, subscriptions, and resource acquisition that need guaranteed cleanup belong in `:post-render` guarded by `this-mounting?`, with cleanup in `:unmount` or a React 19 ref cleanup function.

## Quickstart

Add the necessary dependency to your project:

```clojure
Leiningen: [com.taoensso/tengen "1.2.0"] ; or
deps.edn:   com.taoensso/tengen {:mvn/version "1.2.0"}
```

And set up your namespace imports:

```clojure
(ns my-cljs-ns
  (:require [taoensso.tengen.reagent :as tengen :refer-macros [cmptfn def-cmptfn]]))
```

That is the whole public API. See the `cmptfn` and `def-cmptfn` docstrings for more details.

## Compatibility

Tengen 1.2+ targets Reagent 2.x and npm React/ReactDOM. The test suite includes a React 19 StrictMode smoke test via shadow-cljs/jsdom.

Tengen avoids removed DOM lookup helpers like `findDOMNode`/`reagent.dom/dom-node`; use React ref callbacks for DOM node access.

## FAQ

### Why only Reagent support?

I was most familiar with Reagent, so started there. Extending to other libraries should be straightforward if there is demand.

[Rum][]'s design in particular looks pleasant.

### How is the performance?

Tengen does not add any detectable overhead to your components. It is just a lightweight macro wrapper around Reagent's usual constructor.

### How does this affect reactive atoms, etc.?

It does not. You can continue to use whatever higher-level state management strategies you prefer.

### How do I access DOM nodes?

As usual for Reagent, prefer [ref callbacks][]. When lifecycle code needs the node, keep it in a value established by `:let-mount`. With React 19+, a ref callback can return its cleanup function:

```clojure
(def-cmptfn my-example-component [arg1 arg2]
  :let-mount
  [node_ (atom nil)
   ref-fn
   (fn [node]
     (reset! node_ node)
     (when node
       (fn [] (reset! node_ nil))))]

  :render
  [:div
   {:ref ref-fn}
   arg1
   arg2]

  :post-render
  (when-let [node @node_]
    ;; node is mounted in DOM
    ))
```

If you need React 18 compatibility, clear the atom explicitly in `:unmount`.

### How do I catch render errors?

Experimental, feedback welcome: `:catch` defines a React error-boundary fallback for descendant render/lifecycle errors.

```clojure
(def-cmptfn my-boundary [user-id]
  :render
  [:section [my-risky-child user-id]]

  :catch
  [:div.error
   "Could not render user "
   user-id
   ": "
   (ex-message this-error)])
```

The fallback body receives component args, `:let-mount` values, the last successful `:let-render` values, and `this-error`. Keep it pure; React may render fallback UI more than once before committing. `:post-render` is skipped for fallback commits. When a Tengen descendant throws, `(ex-data this-error)` includes the component id, lifecycle id, mount status, and original non-Error thrown value when relevant.

Like React error boundaries generally, `:catch` catches descendant errors only. It does not catch errors in the same component's own body, event handlers, async callbacks, or server-side rendering. Change the component args to retry automatically; use `^{:key ...}` to force a full remount.

In React StrictMode dev builds, `:catch` currently uses an `UNSAFE_componentWillReceiveProps` hook for argv-change reset, so React may log a dev-only warning. Production behavior is unaffected.

## Documentation

- API reference via [cljdoc][cljdoc]
- Support via [GitHub issues][GitHub issues]

## Funding

You can [help support][sponsor] continued work on this project and [others][my work], thank you!! 🙏

## License

Copyright &copy; 2016-2026 [Peter Taoussanis][].<br/>
Licensed under [EPL 1.0](LICENSE) (same as Clojure).

<!-- Common -->

[GitHub releases]: ../../releases
[GitHub issues]:   ../../issues
[GitHub wiki]:     ../../wiki

[Peter Taoussanis]: https://www.taoensso.com
[sponsor]:          https://www.taoensso.com/sponsor
[my work]:          https://www.taoensso.com/clojure-libraries

<!-- Project -->

[cljdoc]: https://cljdoc.org/d/com.taoensso/tengen/CURRENT/api/taoensso.tengen.reagent

[Clojars SVG]: https://img.shields.io/clojars/v/com.taoensso/tengen.svg
[Clojars URL]: https://clojars.org/com.taoensso/tengen

[Clj tests SVG]:  https://github.com/taoensso/tengen/actions/workflows/clj-tests.yml/badge.svg
[Clj tests URL]:  https://github.com/taoensso/tengen/actions/workflows/clj-tests.yml
[Cljs tests SVG]: https://github.com/taoensso/tengen/actions/workflows/cljs-tests.yml/badge.svg
[Cljs tests URL]: https://github.com/taoensso/tengen/actions/workflows/cljs-tests.yml
[Graal tests SVG]: https://github.com/taoensso/tengen/actions/workflows/graal-tests.yml/badge.svg
[Graal tests URL]: https://github.com/taoensso/tengen/actions/workflows/graal-tests.yml

<!-- Unique -->

[Go]: https://en.wikipedia.org/wiki/Go_game
[lifecycle methods]: https://react.dev/reference/react/Component
[React]: https://react.dev/
[Reagent]: https://github.com/reagent-project/reagent
[Rum]: https://github.com/tonsky/rum
[ref callbacks]: https://react.dev/learn/manipulating-the-dom-with-refs
