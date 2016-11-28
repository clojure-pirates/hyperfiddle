(ns hypercrud.browser.pages.index
  (:require [hypercrud.browser.links :as links]
            [hypercrud.client.core :as hc]))


(defn ui [links navigate-cmp param-ctx]
  [:div
   ;form to populate holes
   [:ul.links
    (->> links                                              ;todo this should be a select
         (sort-by :link/prompt)
         (map (fn [{:keys [:db/id :link/prompt :link/ident] :as link}]
                [:li {:key id}
                 [navigate-cmp (links/query-link #() link param-ctx) prompt]])))]])


;todo we should query the links
(defn query [] {})
