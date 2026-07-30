(ns rirekisho.cohort-test
  (:require [clojure.test :refer [deftest is testing]]
            [rirekisho.cohort :as cohort]))

(deftest projection-carries-no-identifying-field
  (testing "識別項目は引数に渡しても射影に出ない"
    (let [p (cohort/project {:isco "2512" :country "JPN" :seniority "senior"
                             :skills ["clojure" "wasm"]
                             ;; 以下は project が読まないので落ちる
                             :name "川崎 純" :date-of-birth "1985-01-01"})]
      (is (= #{:cohort/isco :cohort/country :cohort/seniority :cohort/skill}
             (set (keys p))))
      (is (empty? (cohort/leaks p))))))

(deftest leaks-catches-a-hand-built-projection
  (testing "手で組んだ射影に識別項目が混ざれば検出する"
    (is (= #{:name} (cohort/leaks {:cohort/isco "2512" :name "川崎 純"})))))

(deftest a-cohort-below-k-is-not-published-at-all
  (let [projections (repeat 4 (cohort/project {:isco "2512" :country "JPN"}))
        {:keys [cohorts suppressed]} (cohort/aggregate projections 5)]
    (testing "丸めるのではなく出さない —— 丸めた数は差分攻撃で戻せる"
      (is (empty? cohorts))
      (is (= 1 suppressed)))))

(deftest a-cohort-at-k-is-published
  (let [projections (repeat 5 (cohort/project {:isco "2512" :country "JPN"
                                               :skills ["clojure"]}))
        {:keys [cohorts suppressed]} (cohort/aggregate projections 5)]
    (is (= 1 (count cohorts)))
    (is (= 0 suppressed))
    (is (= 5 (:cohort/count (first cohorts))))
    (is (= #{"clojure"} (:cohort/skill (first cohorts))))))

(deftest suppression-is-reported-not-silent
  (testing "落とした件数を返さないと、呼び出し側が「全部載っている」と読める"
    (let [projections (concat (repeat 5 (cohort/project {:isco "2512" :country "JPN"}))
                              (repeat 2 (cohort/project {:isco "2166" :country "ITA"})))
          {:keys [cohorts suppressed k]} (cohort/aggregate projections 5)]
      (is (= 1 (count cohorts)))
      (is (= 1 suppressed))
      (is (= 5 k)))))

(deftest a-cohort-never-lists-its-members
  (let [{:keys [cohorts]} (cohort/aggregate
                           (repeat 6 (cohort/project {:isco "2512" :country "JPN"})) 5)]
    (is (every? #(every? cohort/cohort-attributes (keys %)) cohorts))
    (is (not-any? #(contains? % :cohort/members) cohorts))))
