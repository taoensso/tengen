(ns taoensso.tengen-react19-tests
  (:require
   [clojure.string :as str]
   [cljs.test :as test :refer [async deftest is]]
   [reagent.core :as reagent]
   [reagent.dom.client :as rdomc]
   [taoensso.tengen.reagent :refer-macros [def-cmptfn]]
   ["react" :as react]
   ["react-dom/client" :as react-dom-client]
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

(defn- catch-events [events]
  (filterv #(= :catch (first %)) events))

(defn- boundary-post-render-events [events]
  (filterv #(= :boundary-post-render (first %)) events))

(defn- catch-event-data [events]
  (mapv (fn [[_ label _id lifecycle-id mount-label render-label]]
          [label lifecycle-id mount-label render-label])
    (catch-events events)))

(defn- throwing-child-id? [id]
  (and (string? id)
    (str/starts-with? id "taoensso.tengen-react19-tests/throwing-child:")))

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

(def-cmptfn throwing-child [label phase]
  :render
  (if (= phase :render)
    (throw (js/Error. (str "render-" label)))
    [:span {:id "child-ok"} (str "ok-" label)]))

(def-cmptfn react19-boundary [label phase events_]
  :let-mount
  [mount-label (str "mount-" label)]

  :let-render
  [render-label (str "render-" label)]

  :render
  [:section [throwing-child label phase]]

  :post-render
  (swap! events_ conj [:boundary-post-render label mount-label render-label])

  :catch
  (let [data (ex-data this-error)
        lifecycle-id (:lifecycle-id data)]
    (swap! events_ conj [:catch label (:id data) lifecycle-id mount-label render-label])
    [:div {:id "boundary-fallback"} (str "caught-" label "-" (name lifecycle-id))]))

(defn- react19-boundary-host [^js props]
  (reagent/as-element
    [react19-boundary (.-label props) (.-phase props) (.-events props)]))

(defn- render-boundary-host! [^js root label phase events_]
  (.render root
    (react/createElement react19-boundary-host
      #js {:label label, :phase phase, :events events_})))

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

(deftest _react19-error-boundary
  (async done
    (let [root-el (install-dom!)
          events_ (atom [])
          caught_ (atom [])
          root    (react-dom-client/createRoot root-el #js {:onCaughtError #(swap! caught_ conj %1)})]

      (render-boundary-host! root "Ada" :render events_)
      (soon
        (fn []
          (is (= "caught-Ada-render" (.-textContent root-el)))
          (is (= 1 (count @caught_)))
          (let [events (catch-events @events_)]
            (is (= 2 (count events)))
            (is (every? throwing-child-id? (map #(nth % 2) events)))
            (is (= [["Ada" :render "mount-Ada" "render-Ada"]
                    ["Ada" :render "mount-Ada" "render-Ada"]]
                  (catch-event-data @events_))))
          (is (empty? (boundary-post-render-events @events_)))

          (render-boundary-host! root "Grace" nil events_)
          (soon
            (fn []
              (soon
                (fn []
                  (is (= "ok-Grace" (.-textContent root-el)))
                  (is (= 1 (count @caught_)))
                  (let [events (catch-events @events_)]
                    (is (= 2 (count events)))
                    (is (every? throwing-child-id? (map #(nth % 2) events)))
                    (is (= [["Ada"   :render "mount-Ada" "render-Ada"]
                            ["Ada"   :render "mount-Ada" "render-Ada"]]
                          (catch-event-data @events_))))
                  (is (= [[:boundary-post-render "Grace" "mount-Ada" "render-Grace"]]
                        (boundary-post-render-events @events_)))

                  (render-boundary-host! root "Ada" :render events_)
                  (soon
                    (fn []
                      (is (= "caught-Ada-render" (.-textContent root-el)))
                      (is (= 2 (count @caught_)))
                      (let [events (catch-events @events_)]
                        (is (= 4 (count events)))
                        (is (every? throwing-child-id? (map #(nth % 2) events)))
                        (is (= [["Ada"   :render "mount-Ada" "render-Ada"]
                                ["Ada"   :render "mount-Ada" "render-Ada"]
                                ["Ada"   :render "mount-Ada" "render-Ada"]
                                ["Ada"   :render "mount-Ada" "render-Ada"]]
                              (catch-event-data @events_))))
                      (is (= [[:boundary-post-render "Grace" "mount-Ada" "render-Grace"]]
                            (boundary-post-render-events @events_)))
                      (.unmount root)
                      (done))))))))))))

(defmethod test/report [:cljs.test/default :end-run-tests] [m]
  (when-not (test/successful? m)
    (throw (ex-info "ClojureScript tests failed" {}))))
