; yikes, clj (not cljs) because we dont have reagent working with test-cljs yet
(ns hypercrud.ui.control.link-controls-util-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is]]
            [hypercrud.browser.link :as link]
            [hypercrud.ui.control.link-controls-util :as link-controls-util]
            [hypercrud.util.reactive :as reactive]))


(def mock-links
  (for [rel [:my/link :options]
        path [nil "0" "0 :some/attr"]
        dependent? [true false]
        render-inline? [true false]
        managed? [true false]]
    {:link/rel rel
     :link/path path
     :link/dependent? dependent?
     :link/render-inline? render-inline?
     :link/managed? managed?}))

(deftest x []
  (is (= (set (for [rel [:my/link :options]]
                {:link/rel rel
                 :link/path nil
                 :link/dependent? true
                 :link/render-inline? true
                 :link/managed? false}))
         (set (link-controls-util/ui-contextual-links [] true true (reactive/atom mock-links) nil))))

  (is (= (apply set/union (for [rel [:my/link :options]]
                            #{{:link/rel rel
                               :link/path "0"
                               :link/dependent? false
                               :link/render-inline? false
                               :link/managed? true}
                              {:link/rel rel
                               :link/path "0"
                               :link/dependent? false
                               :link/render-inline? false
                               :link/managed? false}
                              {:link/rel rel
                               :link/path "0"
                               :link/dependent? false
                               :link/render-inline? true
                               :link/managed? true}}))
         (into #{} (link-controls-util/ui-contextual-links [0] false false (reactive/atom mock-links) nil))))

  (is (= (set [{:link/rel :my/link
                :link/path "0 :some/attr"
                :link/dependent? false
                :link/render-inline? true
                :link/managed? false}])
         (set (link-controls-util/ui-contextual-links [0 :some/attr] false true (reactive/atom mock-links) [link/options-processor])))))