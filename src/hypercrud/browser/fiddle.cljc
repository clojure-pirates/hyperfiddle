(ns hypercrud.browser.fiddle
  (:require
    [contrib.string :refer [or-str]]
    [cuerdas.core :as str]
    #_[hyperfiddle.ui]))


(defn data-defaults [fiddle]
  #_(when-not (map? fiddle)
    (println (pr-str fiddle)))
  (cond-> fiddle
    (= :query (:fiddle/type fiddle)) (update :fiddle/query or-str "[:find (pull ?e [:db/id *]) :where\n [?e :db/ident :db/add]]")
    (= :entity (:fiddle/type fiddle)) (-> (update :fiddle/pull or-str "[:db/id *]")
                                          (update :fiddle/pull-database or-str "$"))
    (nil? (:fiddle/type fiddle)) (assoc :fiddle/type :blank)))

(defn fiddle-defaults [fiddle route]
  (-> (data-defaults fiddle)
      (update :fiddle/markdown or-str (str/fmt "### %s" (some-> fiddle :fiddle/ident str)))
      (update :fiddle/renderer or-str #?(:clj nil :cljs (-> hyperfiddle.ui/fiddle meta :expr-str)))))
