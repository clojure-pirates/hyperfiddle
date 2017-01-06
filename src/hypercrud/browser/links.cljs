(ns hypercrud.browser.links
  (:require [cljs.reader :as reader]
            [clojure.set :as set]
            [hypercrud.client.core :as hc]
            [hypercrud.client.internal :as internal]
            [hypercrud.compile.eval :refer [eval]]
            [hypercrud.form.q-util :as q-util]
            [hypercrud.util :as util]))


(defn build-params-map [link-ctx param-ctx]
  #_ (assert (not (empty? (-> link-ctx :link-ctx/link :link/find-element))) "dependent query insanity check")
  {:link-dbid (-> link-ctx :link-ctx/link :db/id)
   :query-params (->> (q-util/read-eval-formulas (:link-ctx/formula link-ctx))
                      (util/map-values #(q-util/run-formula % param-ctx)))
   ;; Create a result of shape [?e ?f] with new entities colored
   :create-new-find-elements (->> (-> link-ctx :link-ctx/link :link/find-element)
                                  (mapv (juxt :find-element/name #(hc/*temp-id!* (-> % :find-element/connection :db/id :id))))
                                  (into {}))})


(defn holes-filled? [hole-names params-map]
  (set/subset? (set hole-names) (set (keys (into {} (remove (comp nil? val) params-map))))))


(defn renderable-link? [link params-map]
  (some-> link :link/query
          reader/read-string
          q-util/parse-holes
          (holes-filled? params-map)))


(defn build-link-props [link-ctx param-ctx]
  (let [param-ctx (assoc param-ctx :link-owner (-> link-ctx :link-ctx/link :hypercrud/owner)) ; tx-fn may need this
        tx-fn (if-let [tx-fn (-> link-ctx :link-ctx/link :link/tx-fn)]
                (let [{value :value error :error} (eval tx-fn)]
                  ;; non-fatal error, report it here so user can fix it
                  (if error (js/alert (str "cljs eval error: " error)))
                  value))]
    ;; link-dbid is assumed to be the editor-graph connection

    ;; add-result #(tx/edit-entity (:db/id entity) ident [] [(first %)])
    (if tx-fn
      {:on-click #((:user-swap! param-ctx) (tx-fn param-ctx))}
      (let [params-map (build-params-map link-ctx param-ctx)]
        {:route params-map
         :class (if-not (renderable-link? (:link-ctx/link link-ctx) params-map)
                  "invalid")}))))


(defn link-visible? [link-ctx param-ctx]
  (let [visible-src (:link-ctx/visible? link-ctx)
        visible-fn (if-not (empty? visible-src)
                     (let [{:keys [value error]} (eval visible-src)]
                       (if error (js/alert (str "cljs eval error: " error)))
                       value)
                     (constantly true))]
    (visible-fn param-ctx)))
