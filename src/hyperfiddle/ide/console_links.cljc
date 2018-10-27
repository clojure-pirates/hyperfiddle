(ns hyperfiddle.ide.console-links
  (:require
    [cats.core :refer [mlet]]
    [cats.monad.either :as either]
    [clojure.set :as set]
    [clojure.string :as string]
    [contrib.data :refer [merge-by transpose]]
    [contrib.datomic :refer [ref? enclosing-pull-shape form-traverse]]
    [contrib.reactive :as r]
    [contrib.string :refer [blank->nil]]
    [contrib.try$ :refer [try-either]]
    [datascript.parser :as parser #?@(:cljs [:refer [FindRel FindColl FindTuple FindScalar Variable Aggregate Pull]])]
    [hypercrud.browser.field :as field]
    [hyperfiddle.ide.system-fiddle :as system-fiddle]
    [hyperfiddle.fiddle :as fiddle])
  #?(:clj (:import (datascript.parser FindRel FindColl FindTuple FindScalar Variable Aggregate Pull))))


(defn ^:export system-link? [link-id]
  (and (keyword? link-id)
       (some-> (namespace link-id) (string/starts-with? "hyperfiddle.browser.system-link"))))

(defn path->str [path]
  (blank->nil (string/join " " path)))

(defn hf-detach [path]
  {:db/id (keyword "hyperfiddle.browser.system-link" (str "detach-" (hash path)))
   :hypercrud/sys? true
   :link/rel :hf/detach
   :link/path (path->str path)})

(defn hf-remove [path]
  {:db/id (keyword "hyperfiddle.browser.system-link" (str "remove-" (hash path)))
   :hypercrud/sys? true
   :link/rel :hf/remove
   :link/path (path->str path)})

(defn hf-self [path dbname]
  {:db/id (keyword "hyperfiddle.browser.system-link" (str "edit-" (hash path)))
   :hypercrud/sys? true
   :link/rel :hf/self
   :link/path (path->str path)
   :link/fiddle (system-fiddle/console-edit dbname)})

(defn hf-new [path dbname]
  {:db/id (keyword "hyperfiddle.browser.system-link" (str "new-" (hash path)))
   :hypercrud/sys? true
   :link/rel :hf/new
   :link/path (blank->nil path)
   :link/fiddle (system-fiddle/console-new dbname)})

(defn hf-affix [path dbname]
  {:db/id (keyword "hyperfiddle.browser.system-link" (str "affix-" (hash path)))
   :hypercrud/sys? true
   :link/rel :hf/affix
   :link/path (path->str path)
   :link/fiddle (system-fiddle/console-affix dbname)})

; These are all ref:    :reg/gender, {:reg/gender [:db/id]}, {:reg/gender [:db/ident]}
; These are all id:     :db/id, :db/ident

; hf/new      id under FindColl, ref under FindColl if identity?
; hf/affix    id under ref, ref under ref? Specifically not just id

; hf/self     id, ref
; hf/detach   ref
; hf/remove   id


(defn hf-remove? [id parent-path]
  ;

  ; remove-[] is allowed in edit entity
  ; in hf/new, it is not. (If it is a tempid, it is not – better way) - discard button is the same effect.
  ; But we don't know yet, because we are an empty form sometimes.
  ; So we can't simply generate links by the field, do we need to know the context?
  ; Tempid detection works in inline-branch cases, but do we have an id?
  ; If we don't have an id, the link isn't even valid ... what do we see?
  ; Any given fiddle can be passed a tempid, or not, so we can't know here if we need remove or not.
  ; We should just give it, and the link should not be satisfied or something. Its just not valid on tempids
  ; The control can just not show it in that case i guess.
  (and id (empty? parent-path)))

(defn nested-links-for-field
  ([q dbname schema field parent-path parent-has-id]
   (let [{segment ::field/path-segment} field
         child-path (or (some->> segment (conj parent-path)) parent-path)]
     ; (not= '* segment)
     ; (not (nil? segment)) - nil segments are always ignored
     ; (not= :entity (:fiddle/type fiddle))
     ; (context/attribute-segment? segment)

     ; Don't generate it at the id-in-ref path - generate it at the ref.
     ; editable-ref, really. If we don't know identity we can't edit or link to it.

     (let [fc (= FindColl (type (:qfind q)))
           id-field (field/identity-segment? field)
           ref (::field/data-has-id? field)
           id-under-ref (and id-field parent-has-id)
           ref-under-ref (and ref parent-has-id)]
       (->> [(if ref (hf-self child-path dbname))
             ;(if (and id (seq parent-path)) (hf-edit parent-path dbname)) ; parent ref already did it
             (if (and ref (not id-field)) (hf-detach child-path))
             (if (hf-remove? id-field parent-path) (hf-remove parent-path))
             (if ref-under-ref (hf-affix child-path dbname))
             ;(if (and id-under-ref (seq parent-path)) (hf-affix parent-path dbname)) ; parent ref already did it
             (if (and id-field (empty? parent-path) (not parent-has-id) fc) (hf-new parent-path dbname)) ; Insufficient condition to distinguish from scalar and entity
             ]
            (concat
              (if-not (field/children-identity-only? field)
                (->> (::field/children field)
                     (mapcat #(nested-links-for-field nil dbname schema % child-path (::field/data-has-id? field))))))
            (remove nil?)
            set                                             ; Pulling :db/id and :db/ident together can collide links, for example.
            )))))

(defn system-links-impl [q fields schemas]
  (->> fields
       (filter ::field/source-symbol)
       (mapcat (fn [{:keys [::field/path-segment] :as field}]
                 (let [dbname (str (::field/source-symbol field))
                       schema (get schemas dbname)]
                   (cond->> (nested-links-for-field q dbname schema field [] false)
                     #_(not= :entity (:fiddle/type q))
                     ; nil path means `:find (count ?x) .`
                     #_(cons (hf-new (some-> path-segment vector) dbname))))))))

(defn console-links
  "All sys links can be matched and merged with user-links. Matching is determined by link/rel and link/path"
  [field schemas]
  (let [q (::field/query field)
        missing-fe-wrapper (::field/source-symbol field)]
    (if missing-fe-wrapper
      (system-links-impl q [field] schemas)                 ; wrapper for FindColl, FindTuple, etc? weird.
      (system-links-impl q (::field/children field) schemas))))

(let [f (fn [new-links fiddle]
          (update fiddle :fiddle/links (partial merge-by (juxt :link/rel (comp blank->nil :link/path)) new-links)))]
  (defn inject-console-links [ctx]
    (let [console-links (->> (console-links @(:hypercrud.browser/field ctx) @(:hypercrud.browser/schemas ctx))
                             (map fiddle/auto-link))]
      (update ctx :hypercrud.browser/fiddle #(r/fmap->> % (f console-links))))))

(defn findcoll-links-at [source path]
  (let [id-field (#{:db/id :db/ident #_nil} (last path))
        path (if id-field (butlast path) path)]
    (-> #{(hf-self path (str source))
          (if (= 1 (count path))
            (if id-field (hf-remove path))
            (hf-detach path))
          (if (= 1 (count path))
            (if id-field (hf-new path (str source)))
            (hf-affix path (str source)))}
        (disj nil))))

(defn query-links+ [schemas q data]
  (mlet [{find :qfind} (try-either (parser/parse-query q))]
    (condp = (type find)
      FindColl (let [{e :element} find]
                 (condp = (type e)
                   Pull (let [{{pattern :value} :pattern
                               {source :symbol} :source} e
                              schema (get schemas (str source))]
                          (->> (enclosing-pull-shape schema pattern data)
                               form-traverse
                               (filter (comp (partial ref? schema) last))
                               (mapcat (partial findcoll-links-at source))))
                   Variable nil
                   Aggregate nil))
      FindRel nil
      FindTuple nil
      FindScalar (condp = (type (:element find))
                   Pull nil
                   Variable nil
                   Aggregate nil)))
  )

(defn impl [schemas {:keys [fiddle/type fiddle/query fiddle/pull fiddle/pull-database]} data]
  (case type
    :entity nil
    :query (query-links+ schemas query data)
    :blank nil))

(defn inject-console-links' [ctx]
  (impl @(:hypercrud.browser/schemas ctx) @(:hypercrud.browser/fiddle ctx) @(:hypercrud.browser/data ctx)))