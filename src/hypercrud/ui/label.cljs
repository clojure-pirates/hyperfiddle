(ns hypercrud.ui.label
  (:require [cuerdas.core :as str]
            [hypercrud.browser.link :as link]
            [hypercrud.ui.tooltip :as tooltip]
            [hypercrud.util.core :as util]))


(defn attribute-schema-human [attr]
  (->> (-> attr
           (util/update-existing :db/cardinality :db/ident)
           (util/update-existing :db/valueType :db/ident)
           (util/update-existing :db/unique :db/ident)
           (select-keys [:db/valueType :db/cardinality :db/unique]))
       (reduce-kv (fn [acc k v] (conj acc v)) [])))

(defn label-inner [field ctx]
  (let [label (-> ctx :attribute :db/ident str)
        ;help-text (apply str (interpose " " (attribute-schema-human (:attribute ctx))))
        help-text (-> ctx :attribute :db/doc)]
    (if-not (str/empty-or-nil? help-text)
      [tooltip/fast-hover-tooltip-managed
       {:label help-text
        :position :below-right}
       [:span.hyperfiddle-help label]]
      [:span label])))