This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---

# `v1.2.0` (2026-07-02)

- **Dependency**: [on Clojars](https://clojars.org/com.taoensso/tengen/versions/1.2.0)
- **Versioning**: [Break Versioning](https://www.taoensso.com/break-versioning)

Tengen v1.2 modernizes the Reagent backend for Reagent 2 and npm React/ReactDOM 19, and adds an experimental Reagent error-boundary API. Expected to be **non-breaking** for Tengen's public API; please test carefully if your app depends on React/Reagent lifecycle timing.

Please report unexpected problems to [GitHub](https://github.com/taoensso/tengen/issues).

\- [Peter Taoussanis](https://www.taoensso.com)

Quick highlights:

- Reagent 2 + React 19 compatibility
- Experimental `:catch` error-boundary fallback support
- Better React DevTools/component-stack names and lifecycle error context
- Exported clj-kondo hooks for Tengen macros

## Since `v1.1.0` (2021-01-12)

### Changes (please check!)

- \[mod] Update Reagent internals for Reagent 2/React 19, including `:display-name`, StrictMode-tolerant `:let-mount` lifecycle state, and clearer lifecycle error wrapping \[b6bfdbc]
- \[mod] Update dependencies: Reagent 2.0.1, ClojureScript 1.12.145, Clojure 1.12.5, Encore 3.168.0, Truss 2.3.0; test against npm React/ReactDOM 19.2.7 \[430cc6d] \[06fc9c4]
- \[mod] Sync project, README, and GitHub workflow style with modern Taoensso libs; add CLJ/CLJS/Graal CI workflows \[61e3d22]
- \[mod] Drop Clojure 1.9 testing \[61e3d22]

### New

- \[new] Add experimental `:catch` error-boundary fallback support for Reagent components \[2a78589]
- \[new] Add React 19 StrictMode smoke tests via shadow-cljs/jsdom \[06fc9c4]
- \[new] Add exported clj-kondo hooks for `cmptfn`/`def-cmptfn` binding forms \[0428027]
- \[new] Add cljdoc config \[4db04bc]

### Docs

- \[doc] Document React ref callback pattern for DOM nodes, avoiding removed DOM lookup helpers \[2325718]
- \[doc] Document experimental `:catch` API, caveats, and StrictMode dev warning \[2a78589]

---

## v1.1.0 - 2021 Jan 12

```clojure
[com.taoensso/tengen "1.1.0"]
```

> This is a release to update dependencies. Should be **non-breaking**.  
> See [here](https://github.com/taoensso/encore#recommended-steps-after-any-significant-dependency-update) for recommended steps when updating any Clojure/Script dependencies.

**Changes** since `v1.0.0`:

* Update internals to latest Reagent (1.0.0) API, no external Tengen API changes.


**New** since `v1.0.0`:

* Improved error messages when component-fns throw.


## v1.0.0 - 2020 Aug 29

```clojure
[com.taoensso/tengen "1.0.0"]
```

Identical to `1.0.0-RC1`.


## v1.0.0-RC1 - 2016 Oct 15

```clojure
[com.taoensso/tengen "1.0.0-RC1"]
```

Initial public release.
