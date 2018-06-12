(ns hypercrud.ui.attribute.edn
  (:require [clojure.set :as set]
            [contrib.datomic-tx :as tx]
            [contrib.reactive :as r]
            [contrib.ui :refer [edn-block edn-inline-block]]
            [hypercrud.browser.link :as link]))


(defn ^:export edn-many [value ctx props]
  (let [valueType @(r/cursor (:hypercrud.browser/fat-attribute ctx) [:db/valueType :db/ident])
        value (-> (if (= valueType :db.type/ref)
                    (map :db/id value)
                    value)
                  set)
        props (update props :read-only #(or % (nil? @(r/cursor (:cell-data ctx) [:db/id]))))
        change! (fn [user-val]
                  (let [user-val (set user-val)
                        rets (set/difference value user-val)
                        adds (set/difference user-val value)]
                    ((:user-with! ctx) (tx/edit-entity (-> ctx :cell-data deref :db/id)
                                                       (:hypercrud.browser/attribute ctx)
                                                       rets adds))))
        widget (case (:hyperfiddle.ui/layout ctx :hyperfiddle.ui.layout/block)
                 :hyperfiddle.ui.layout/block edn-block
                 :hyperfiddle.ui.layout/table edn-inline-block)]
    [:div
     [widget value change! props]]))

(defn ^:export edn [value ctx props]
  (let [props (update props :read-only #(or % (nil? @(r/cursor (:cell-data ctx) [:db/id]))))
        change! #((:user-with! ctx) (tx/update-entity-attr @(:cell-data ctx) @(:hypercrud.browser/fat-attribute ctx) %))
        widget (case (:hyperfiddle.ui/layout ctx :hyperfiddle.ui.layout/block)
                 :hyperfiddle.ui.layout/table edn-inline-block
                 :hyperfiddle.ui.layout/block edn-block)]
    [:div
     [widget value change! props]]))
