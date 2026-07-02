(ns taoensso.tengen-react19-tests
  (:require
   [cljs.test :as test :refer [async deftest is]]
   [reagent.dom.client :as rdomc]
   [taoensso.tengen.reagent :refer-macros [def-cmptfn]]
   ["jsdom" :refer [JSDOM]]))

(defn- install-dom! []
  (let [dom  (JSDOM. "<!doctype html><html><body><div id=\"root\"></div></body></html>")
        win  (.-window dom)
        doc  (.-document win)
        root (.getElementById doc "root")]
    (set! (.-window    js/global) win)
    (set! (.-document  js/global) doc)
    (set! (.-navigator js/global) (.-navigator win))
    (set! (.-Node      js/global) (.-Node win))
    (set! (.-HTMLElement js/global) (.-HTMLElement win))
    (when-not (.-requestAnimationFrame js/global)
      (set! (.-requestAnimationFrame js/global) (fn [f] (js/setTimeout f 0))))
    (when-not (.-cancelAnimationFrame js/global)
      (set! (.-cancelAnimationFrame js/global) js/clearTimeout))
    root))

(defn- soon [f]
  (js/setTimeout f 30))

(defn- event-count [events event]
  (count (filter #{event} events)))

(def-cmptfn react19-cmpt [label events_]
  :let-mount
  [mount-label (do (swap! events_ conj [:let-mount label]) (str "mount-" label))
   node_       (atom nil)]

  :let-render
  [render-label (do (swap! events_ conj [:let-render label this-mounting? mount-label])
                    (str "render-" label))]

  :render
  (do
    (swap! events_ conj [:render label this-mounting? mount-label render-label])
    [:div {:id "react19-cmpt" :ref #(reset! node_ %)} label])

  :post-render
  (swap! events_ conj [:post-render label this-mounting? (some? @node_)])

  :unmount
  (swap! events_ conj [:unmount label mount-label render-label]))

(deftest _react19-strict-mode-smoke
  (async done
    (let [root-el (install-dom!)
          root    (rdomc/create-root root-el)
          events_ (atom [])]
      (rdomc/render root [react19-cmpt "Ada" events_] nil true)
      (soon
        (fn []
          (let [events @events_]
            (is (= "Ada" (.-textContent root-el)))
            (is (= 1 (event-count events [:let-mount "Ada"])))
            (is (= 2 (event-count events [:let-render "Ada" true "mount-Ada"])))
            (is (= 2 (event-count events [:render "Ada" true "mount-Ada" "render-Ada"])))
            (is (= 1 (event-count events [:unmount "Ada" "mount-Ada" "render-Ada"])))
            (is (= 2 (event-count events [:post-render "Ada" true true]))))

          (rdomc/unmount root)
          (soon
            (fn []
              (let [events @events_]
                (is (= "" (.-textContent root-el)))
                (is (= 1 (event-count events [:let-mount "Ada"])))
                (is (= 2 (event-count events [:unmount "Ada" "mount-Ada" "render-Ada"]))))
              (done))))))))
