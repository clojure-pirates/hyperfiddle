(ns hypercrud.browser.core
  (:require [cljs.pprint :as pprint]
            [clojure.string :as string]
            [hypercrud.browser.base-64-url-safe :as base64]
            [hypercrud.browser.pages.entity :as entity]
            [hypercrud.browser.pages.index :as index]
            [hypercrud.client.core :as hc]
            [hypercrud.ui.table :as table]))


(defn query [forms state page-rel-path]
  (let [path-params (string/split page-rel-path "/")]
    (cond
      (and (= (second path-params) "query") (= 3 (count path-params))) (let [[metatype _ q] path-params]
                                                     (table/query ((keyword metatype) forms) (base64/decode q)))
      (and (= (second path-params) "entity") (= 3 (count path-params))) (let [[metatype _ eid] path-params]
                                                      (entity/query (js/parseInt eid 10) state (keyword metatype) forms))
      (and (= (first path-params) "") (= 1 (count path-params))) (index/query)
      :else {}
      )))


(defn ui [cur transact! graph forms index-queries page-rel-path]
  [:div
   [:div.hc-node-view
    (let [path-params (string/split page-rel-path "/")]
      (cond
        (and (= (second path-params) "query") (= 3 (count path-params))) (let [[metatype _ q] path-params]
                                                       [table/table graph forms (hc/select graph ::table/query) (keyword metatype)])
        (and (= (second path-params) "entity") (= 3 (count path-params))) (let [[metatype _ eid] path-params]
                                                        (entity/ui cur transact! graph (keyword metatype) forms (js/parseInt eid 10)))
        (and (= (first path-params) "") (= 1 (count path-params))) (index/ui index-queries)
        :else [:div "no route for: " page-rel-path]
        ))]
   [:hr]
   [:pre (with-out-str (pprint/pprint @cur))]])
