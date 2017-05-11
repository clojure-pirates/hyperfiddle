(ns hypercrud.browser.links
  (:require [cljs.reader :as reader]
            [clojure.set :as set]
            [hypercrud.client.core :as hc]
            [hypercrud.client.tx :as tx]
            [hypercrud.compile.eval :refer [eval-str]]
            [hypercrud.form.q-util :as q-util]
            [hypercrud.types :refer [->DbId]]
            [hypercrud.util :as util]
            [promesa.core :as p]
            [hypercrud.browser.connection-color :as connection-color]
            [hypercrud.browser.link-util :as link-util]
            [reagent.core :as r]))


(defn auto-formula [anchor]                                 ; what about long-coersion?
  ; Future improvement:
  ; we already have this info in the runtime param-ctx, so we could delay until formula runtime
  ; and not look at the anchor at all and bypass the formula read-eval.

  ; this is a 3x3 matrix - repeating, entity, attribute. Find element is not part of the matrix.
  ; link-query's always have a find-element, if they have an attribute
  ; link-entity's never do, despite having an attribute.
  (let [{r :anchor/repeating? e :anchor/find-element a :anchor/attribute} anchor]
    (cond

      ; attr edit
      (and r a)
      (pr-str {:entity `(fn [ctx#]
                          (case (-> ((:schema ctx#) (-> ctx# :attribute :attribute/ident)) :attribute/cardinality :db/ident)
                            :db.cardinality/one (get-in ctx# [:value :db/id])
                            :db.cardinality/many (mapv :db/id (get ctx# :value))))})

      ; attr create (managed, see auto-txfn)
      (and (not r) a)
      ; inherit parent since the fe is never explicitly set by user
      ; it would be more correct to use the FE if we have it, but
      ; that information is guaranteed to be the same?
      (pr-str {:entity `(fn [ctx#]
                          (assert (-> ctx# :entity))
                          (assert (-> ctx# :entity :db/id :conn-id))
                          (->DbId (-> (str (-> ctx# :entity :db/id :id) "."
                                           (-> ctx# :attribute :attribute/ident) "."
                                           (case (-> ((:schema ctx#) (-> ctx# :attribute :attribute/ident)) :attribute/cardinality :db/ident)
                                             :db.cardinality/one nil
                                             :db.cardinality/many (inc (count (:value ctx#)))))
                                      hash js/Math.abs - str)
                                  (-> ctx# :entity :db/id :conn-id)))})

      ; entity edit
      (and r (not a))
      (pr-str {:entity `(fn [ctx#]
                          (get-in ctx# [:entity :db/id]))})

      ; entity create
      ; is it managed or not? We need a connection. Either we got the find-element, or
      ; we are managed. If we're managed, we need an entity in scope, to conjure a connection.
      ; So despite not really needing the value in scope, we need the connection, so we need the value.
      ; This is counter intuitive. It only happens for sys links. Regular links set the linkentity/connection
      ; so don't have this problem.
      (and (not r) (not a))
      (pr-str {:entity `(fn [ctx#]
                          (->DbId (-> (str (-> ctx# :entity :db/id :id) "."
                                           "."
                                           ; this is gonna collide, how do we know how to generate unique id here
                                           )
                                      hash js/Math.abs - str)
                                  (or ~(-> e :find-element/connection :db/id :id)
                                      (-> ctx# :entity :db/id :conn-id))))})

      ; naked
      (and (not r) (not e) (not a)) nil

      ; relation edit (this is not really a thing)
      ; If this is a thing, it probably is a query with named params and a custom formula.
      (and r (not e) (not a)) nil

      :else (assert false (str "auto-formula matrix - missing pattern: " (util/pprint-str [r e a]))))))

(declare build-link-props)

(defn auto-txfn [anchor]
  (let [{r :anchor/repeating? e :anchor/find-element a :anchor/attribute ident :anchor/ident} anchor]
    (cond

      ; link-query's always have a find-element
      ; link-entity's never do
      ; that bit is not interesting here.

      (and (not r) a)                                       ; attr create
      (pr-str `(fn [ctx# show-popover!#]
                 (let [new-dbid# (->DbId (-> (str (-> ctx# :entity :db/id :id) "."
                                                  (-> ctx# :attribute :attribute/ident) "."
                                                  (case (-> ((:schema ctx#) (-> ctx# :attribute :attribute/ident)) :attribute/cardinality :db/ident)
                                                    :db.cardinality/one nil
                                                    :db.cardinality/many (inc (count (:value ctx#)))))
                                             hash js/Math.abs - str)
                                         (-> ctx# :entity :db/id :conn-id))]
                   (-> (show-popover!#)
                       (p/then (fn [tx-from-modal#]
                                 {:tx (concat
                                        tx-from-modal#
                                        (tx/edit-entity (-> ctx# :entity :db/id)
                                                        (-> ctx# :attribute :attribute/ident)
                                                        [] [new-dbid#]))}))))))

      (and r (not a) (= ident :remove))
      (pr-str `(fn [ctx#]
                 {:tx [[:db.fn/retractEntity (-> ctx# :entity :db/id)]]}))


      :else nil)))

(defn build-url-params-map
  ([domain project link-dbid formula-str param-ctx]
    ;(assert project)                                         ; safe - maybe constructing it now
   {:domain domain
    :project project
    :link-dbid link-dbid #_:id
    :query-params (try                                      ; todo return monad
                    (->> (q-util/read-eval-formulas formula-str)
                         (util/map-values #(q-util/run-formula! % param-ctx)))
                    (catch js/Error e {}))})
  ([link formula-str param-ctx]
   (build-url-params-map
     (-> link :hypercrud/owner :database/domain)
     (-> link :hypercrud/owner :database/ident)
     (-> link :db/id)
     formula-str
     param-ctx))
  ([anchor param-ctx]
   (build-url-params-map (:anchor/link anchor) (:anchor/formula anchor) param-ctx)))


(defn holes-filled? [hole-names query-params-map]
  (set/subset? (set hole-names) (set (keys (into {} (remove (comp nil? val) query-params-map))))))

(defn anchor-valid? [link route]                            ; could return monad to say why
  ; We specifically hydrate this deep just so we can validate anchors like this.
  (case (link-util/link-type link)
    :link-query (some-> link :link/request
                        q-util/safe-parse-query-validated
                        q-util/parse-param-holes
                        (holes-filled? (:query-params route)))
    :link-entity (not= nil (-> route :query-params :entity))
    :link-blank true))

(defn anchor-valid?' [anchor route]
  (anchor-valid? (:anchor/link anchor) route))

(defn anchor-tooltip [link url-params param-ctx]
  (case (:display-mode param-ctx)
    :xray (if (anchor-valid? link url-params)
            [nil (pr-str (:query-params url-params))]
            [:warning (pr-str (:query-params url-params))])
    nil))

(defn build-link-props-raw [route link param-ctx]           ; param-ctx is for display-mode
  ; doesn't handle tx-fn - meant for the self-link. Weird and prob bad.
  {:route route
   :style {:color (connection-color/connection-color (-> link :hypercrud/owner :db/id :id))}
   :tooltip (anchor-tooltip link route param-ctx)
   :class (if-not (anchor-valid? link route) "invalid")})



; if this is driven by anchor, and not route, it needs memoized.
; the route is a fn of the formulas and the formulas can have effects
; which have to be run only once.
(defn build-link-props [anchor param-ctx]
  (let [param-ctx (assoc param-ctx :link-owner (-> anchor :anchor/link :hypercrud/owner)) ; txfn may need this
        txfn (if-let [txfn-str (:anchor/tx-fn anchor)]
               (let [{value :value error :error} (eval-str txfn-str)]
                 ; non-fatal error, report it here so user can fix it
                 (if error (js/alert (str "cljs eval error: " error))) ; return monad so tooltip can draw the error
                 value))
        route (if (:anchor/link anchor) (build-url-params-map anchor param-ctx)) #_"return monad so tooltip can draw the error?"
        route-props (if route (build-link-props-raw route (:anchor/link anchor) param-ctx))]
    (doall
      (merge
        (if txfn
          ; do we need to hydrate any dependencies in this chain?
          {:txfn #(let [result (txfn param-ctx %)]          ; txfn may be sync or async
                    (-> (if-not (p/promise? result) (p/resolved result) result)
                        (p/branch (:user-swap! param-ctx)
                                  (fn cancelled [why] nil))))})

        (if (and txfn (:anchor/link anchor))                ; default case is a syslink
          ; this is a special case where due to the txfn the embed goes in a popover
          {:popover (fn [state]
                      ; assumes everything is hydrated
                      [:div
                       (case (:display-mode param-ctx)
                         :xray [(:navigate-cmp param-ctx) route-props "self"]
                         nil)
                       [hypercrud.browser.core/safe-ui      ; cycle
                        route
                        (-> param-ctx
                            (dissoc :result :entity :attribute :value :layout)
                            (assoc :user-swap!
                                   (fn [{:keys [tx route]}]
                                     (assert (not route) "popups not allowed to route")
                                     (swap! state tx/into-tx tx)
                                     ; watch this and re-hydrate
                                     )))]])})

        route-props))))

(defn link-visible? [anchor param-ctx]
  (let [visible-src (:anchor/visible? anchor)
        visible-fn (if-not (empty? visible-src)
                     (let [{:keys [value error]} (eval-str visible-src)]
                       (if error (js/alert (str "cljs eval error: " error)))
                       value)
                     (constantly true))]
    (visible-fn param-ctx)))
