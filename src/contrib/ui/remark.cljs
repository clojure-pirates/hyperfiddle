(ns contrib.ui.remark
  (:require
    ;["@hyperfiddle/remark-generic-extensions/lib/browser.min" :as remark-generic-extensions] ; works in node
    [clojure.set]
    [clojure.string]
    ; Refrain from contrib imports so this is more suitable for userland
    [goog.object]
    [prop-types]
    [reagent.core :as reagent]
    ;[remark] ; works in node
    ;[remark-react] ; works in node
    ))


(def remark                                                 ; todo need browser shim for path (path-browserify)
  (cond
    (exists? js/remark) js/remark
    (exists? js/require) (or (js/require "remark")
                             (throw (js/Error. "require('remark') failed")))
    :else (throw (js/Error. "js/remark is missing"))))

(def remark-react                                           ; todo npm deps chokes on hast-util-sanitize/lib/github.json
  (cond
    (exists? js/remarkReact) js/remarkReact
    (exists? js/require) (or (js/require "remark-react")
                             (throw (js/Error. "require('remark-react') failed")))
    :else (throw (js/Error. "js/remarkReact is missing"))))

(def remark-generic-extensions
  (cond
    (exists? js/remarkGenericExtensions) js/remarkGenericExtensions
    (exists? js/require) (or (js/require "@hyperfiddle/remark-generic-extensions/lib/browser")
                             (throw (js/Error. "require('remark-generic-extensions') failed")))
    :else (throw (js/Error. "js/remarkGenericExtensions is missing"))))

(defn adapt-props [props]
  (-> props
      (dissoc :content :argument)                           ; need children downstack
      (clojure.set/rename-keys {:className :class})))

(defn extension [k f]
  (reagent/create-class
    {:display-name (str "markdown-" (name k))
     :context-types #js {:ctx (.-object prop-types)}
     :reagent-render (fn [{:keys [content argument] :as props}]
                       (let [ctx (goog.object/get (.-context (reagent/current-component)) "ctx")
                             content (if-not (= content "undefined") content)
                             argument (if-not (= argument "undefined") argument)]
                         (f k content argument (adapt-props props) ctx)))}))

(defn -remark-instance! [extensions]
  (let [extensions (reduce-kv (fn [acc k v]
                                (assoc acc k (reagent/reactify-component
                                               (extension k v))))
                              (empty extensions)
                              extensions)]
    (-> (remark)
        (.use remark-generic-extensions
              (clj->js
                {"elements" (into {} (map vector (keys extensions) (repeat {"html" {"properties" {"content" "::content::" "argument" "::argument::"}}})))}))
        (.use remark-react (clj->js
                             {"sanitize" false
                              "remarkReactComponents" extensions}))
        (cond->
          (exists? js/remarkComments) (.use js/remarkComments #js {"beginMarker" "" "endMarker" ""})
          (exists? js/remarkToc) (.use js/remarkToc)))))

(defn remark! [& [extensions]]
  ; remark creates react components which don't evaluate in this stack frame
  ; so dynamic scope is not helpful to communicate values to remark plugins
  ; Reagent + react-context: https://github.com/reagent-project/reagent/commit/a8ec0d219bbd507f51a4d9276c4a1dcc020245af
  (let [remark-instance (-remark-instance! extensions)]
    (reagent/create-class
      {:display-name "markdown"
       :reagent-render
       (fn [value & [?ctx]]
         (when-not (clojure.string/blank? value)
           (let [c (-> remark-instance (.processSync value #js {"commonmark" true}) .-contents)
                 content (-> c .-props .-children) #_"Throw away remark wrapper div"]
             content)))

       :get-child-context
       (fn []
         (this-as this
           (let [[_ value ?ctx] (reagent/argv this)]
             #js {:ctx ?ctx})))

       :child-context-types #js {:ctx (.-object prop-types)}})))
