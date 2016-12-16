(ns hypercrud.ui.multi-select
  (:require [hypercrud.client.tx :as tx]
            [hypercrud.ui.auto-control :refer [auto-control]]))


(defmulti multi-select-markup (fn [click-add! control-tuples] :default))


(defn multi-select* [markupfn entity add-item! {:keys [field user-swap!] :as widget-args}]
  (assert false "assoc entity no longer works. todo")
  (let [ident (-> field :field/attribute :attribute/ident)
        value (get entity ident)
        control-tuples (seq (mapv (fn [eid]
                                    (let [click-remove! #(user-swap! {:tx (tx/edit-entity (:db/id entity) ident [eid] nil)})
                                          new-args (-> widget-args
                                                       ; todo this should be hc/with
                                                       (assoc-in [:field :cardinality] :db.cardinality/one))
                                          control [auto-control (assoc entity ident eid) new-args]]
                                      [eid click-remove! control]))
                                  value))]
    (markupfn add-item! control-tuples)))


(defmethod multi-select-markup :default [click-add! control-tuples & [css-class]]
  [:div.value {:class css-class}
   (map (fn [[eid click-remove! control]]
          ^{:key (str eid)}                                 ;(str eid) so this works when eid is nil
          [:div.multi-select-group
           [:button {:on-click click-remove!} "-"]
           control])
        control-tuples)
   [:button {:on-click click-add!} "+"]])
