(ns contrib.datomic-test
  (:require
    [clojure.test :refer [deftest is]]
    [contrib.datomic :refer [pull-shape pulled-tree-derivative enclosing-pull-shape
                             pull-traverse pull-shape-union]]
    [fixtures.ctx :refer [schema result-coll]]
    [fixtures.domains]))


(def pull-pattern-2
  '[:a/a
    [:a/comp-one :as "comp-one"]
    :a/comp-many
    *
    (limit :a/v 1)
    {(default :a/s {}) [* :b/a :b/b {:c/a [*]} {:c/b [:d/a]}]
     [:a/t :as "T"] [*]
     (limit :a/u 2) [*]
     :a/x [*]}
    (default :a/z "a/z default")])

(def pull-pattern-1 '[:reg/email
                      :reg/age
                      *
                      {:reg/gender [:db/ident]
                       :reg/shirt-size [:db/ident
                                        *]}
                      :db/id])

(deftest pull-shape-
  []
  (is (= [:a/a :a/comp-one :a/comp-many :a/v #:a{:s [:b/a :b/b #:c{:a []} #:c{:b [:d/a]}], :t [], :u [], :x []} :a/z]
         (pull-shape pull-pattern-2)))
  (is (= [:reg/email
          :reg/age
          #:reg{:gender [:db/ident],
                :shirt-size [:db/ident]}
          :db/id]
         (pull-shape pull-pattern-1)))
  (is (= (pull-shape [:db/id
                      :hyperblog.post/sort-index1
                      :hyperblog.post/published
                      :hyperblog.post/hidden
                      :fiddle/ident
                      {:hyperblog.nav/children 1}
                      {:hyperblog.post/related 1}
                      :hyperblog.post/title])
         [:db/id
          :hyperblog.post/sort-index1
          :hyperblog.post/published
          :hyperblog.post/hidden
          :fiddle/ident
          :hyperblog.post/title])))

(def pulled-tree-1 {:db/id 17592186046765,
                    :hyperfiddle/owners [#uuid "acd054a8-4e36-4d6c-a9ec-95bdc47f0d39"],
                    :reg/email "elizabeth@example.com",
                    :reg/age 65,
                    :reg/gender {:db/ident :gender/female},
                    :reg/shirt-size
                    {:db/id 17592186046209,
                     :db/ident :shirt-size/womens-medium,
                     :hyperfiddle/owners [#uuid "acd054a8-4e36-4d6c-a9ec-95bdc47f0d39"],
                     :reg/gender {:db/id 17592186046204}}})

(def derivative-tests
  [["nested :many"
    fixtures.domains/schema
    {:db/id 17592186045942,
     :domain/home-route "",
     :domain/databases
     [{:db/id 17592186046525,
       :domain.database/name "$",
       :domain.database/record
       {:db/id 17592186046087,
        :database/uri
        #uri "datomic:free://datomic:4334/foo-ak"}}],}
    [:db/id :domain/home-route {:domain/databases [:db/id :domain.database/name {:domain.database/record [:db/id :database/uri]}]}]]
   ])

(deftest pulled-tree-derivative-2
  []
  (let [t derivative-tests]
    (doseq [[doc schema tree derivative] t]
      (is (= (pulled-tree-derivative schema tree)
             derivative)
          doc)))
  )

(deftest pulled-tree-derivative-1
  []
  (is (= [:db/id]
         (pulled-tree-derivative schema {:db/id 17592186046204})))

  (is (= [:db/id :db/ident :hyperfiddle/owners #:reg{:gender [:db/id]}]
         (pulled-tree-derivative schema {:db/id 17592186046209,
                                         :db/ident :shirt-size/womens-medium,
                                         :hyperfiddle/owners [#uuid "acd054a8-4e36-4d6c-a9ec-95bdc47f0d39"],
                                         :reg/gender {:db/id 17592186046204}})))

  (is (= [:db/id
          :hyperfiddle/owners
          :reg/email
          :reg/age
          #:reg{:gender [:db/ident]}
          #:reg{:shirt-size [:db/id :db/ident :hyperfiddle/owners #:reg{:gender [:db/id]}]}]
         (pulled-tree-derivative schema pulled-tree-1)))
  )

(deftest pull-shape-union-
  []
  (is (= [:db/id :db/ident #:reg{:gender [:db/id :db/ident]}]
         (pull-shape-union [:db/id {:reg/gender [:db/id]}]
                           [:db/ident {:reg/gender [:db/ident]}])))

  (is (= #:reg{:gender [:db/id :db/ident]}
         (pull-shape-union {:reg/gender [:db/id]}
                           {:reg/gender [:db/ident]})))

  (is (= [:db/id :db/ident #:reg{:gender [:db/id :db/ident]}]
         (pull-shape-union [:db/id {:reg/gender [:db/id]}]
                           [:db/ident {:reg/gender [:db/ident]}])))

  (is (= [:db/id :db/ident #:reg{:gender [:db/id :db/ident]}]
         (pull-shape-union []
                           [:db/id {:reg/gender [:db/id]}]
                           [:db/ident {:reg/gender [:db/ident]}])))

  (def pull-pattern-3 [:reg/email :reg/age #:reg{:gender [:db/ident], :shirt-size [:db/ident]} :db/id])
  (def result-derivatives [[:db/id
                            :hyperfiddle/owners
                            :reg/email
                            :reg/age
                            #:reg{:gender [:db/ident]}
                            #:reg{:shirt-size [:db/id :db/ident :hyperfiddle/owners #:reg{:gender [:db/id]}]}]
                           [:db/id
                            :hyperfiddle/owners
                            :reg/email
                            :reg/name
                            :reg/age
                            #:reg{:gender [:db/ident]}
                            #:reg{:shirt-size [:db/id :db/ident :hyperfiddle/owners #:reg{:gender [:db/id]}]}]])
  (is (= [:reg/email
          :reg/age
          :db/id
          :hyperfiddle/owners
          :reg/name
          #:reg{:gender [:db/ident], :shirt-size [:db/ident :db/id :hyperfiddle/owners #:reg{:gender [:db/id]}]}]
         (apply pull-shape-union pull-pattern-3 result-derivatives)))
  )



(deftest enclosing-pull-shape-
  []
  (enclosing-pull-shape schema [] [{:db/ident :foo} {:db/id 10 :db/ident :yo}])
  (enclosing-pull-shape schema [:db/ident] [])
  (enclosing-pull-shape schema [] [{:db/id 17592186046209,
                                    :db/ident :shirt-size/womens-medium,
                                    :hyperfiddle/owners [#uuid "acd054a8-4e36-4d6c-a9ec-95bdc47f0d39"],
                                    :reg/gender {:db/id 17592186046204}}
                                   {:db/id 17592186046209,
                                    :db/ident :shirt-size/womens-medium,
                                    :reg/gender {:db/id 17592186046204}}
                                   ])
  (enclosing-pull-shape schema (pull-shape pull-pattern-1) result-coll)
  (enclosing-pull-shape schema (pull-shape pull-pattern-1) [])
  )

(deftest form-traverse-
  []
  (is (= (pull-traverse [:db/ident])
         (pull-traverse [:db/id])
         (pull-traverse [:db/id :db/ident])
         '([])))

  (is (= (pull-traverse [:reg/gender])
         (pull-traverse [{:reg/gender [:db/ident]}])
         (pull-traverse [{:reg/gender [:db/id]}])
         (pull-traverse [{:reg/gender [:db/id :db/ident]}])
         (pull-traverse [{:reg/gender []}])
         '([:reg/gender])))



  (is (= (pull-traverse [:reg/gender
                         :db/id                             ; In place order
                         {:reg/shirt-size [:db/ident
                                           :reg/gender
                                           :db/ident
                                           :db/id]}
                         :db/id                             ; Ignored, use first
                         ])
         '([:reg/gender]
            []
            [:reg/shirt-size]
            [:reg/shirt-size :reg/gender])))
  )