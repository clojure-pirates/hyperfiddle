(ns contrib.reagent
  (:require
    [goog.object]
    [reagent.core :as reagent]))


(defn fragment [react-key & xs]
  (js/reactCreateFragment (clj->js {react-key (map reagent/as-element xs)})))

;(defn wrap-naked-string "wrap naked strings into divs" [C v]
;  (if (string? v) [:div v] v)
;  #_(let [hiccup-template (f value)]                          ; terrifying
;    (if (vector? hiccup-template)
;      hiccup-template
;      [C hiccup-template])))

(def with-react-context
  ; Reagent + react-context: https://github.com/reagent-project/reagent/commit/a8ec0d219bbd507f51a4d9276c4a1dcc020245af
  (reagent/create-class
    {:display-name "with-react-context"
     :reagent-render (fn [context template]
                       template)
     :child-context-types #js {"cljs-context" js/propTypes.object}
     :get-child-context (fn []
                          (this-as this
                            (let [[_ context template] (reagent/argv this)]
                              #js {"cljs-context" context})))
     }))

(defn from-react-context [f]
  (reagent/create-class
    {:context-types #js {"cljs-context" js/propTypes.object}
     :reagent-render (fn [& args]
                       (let [context (goog.object/get (.-context (reagent/current-component)) "cljs-context")]
                         (into [f context] args)))
     }))
