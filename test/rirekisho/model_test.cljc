(ns rirekisho.model-test
  (:require [clojure.test :refer [deftest is testing]]
            [rirekisho.model :as model]))

(def ^:private base
  {:name "川崎 純" :name-kana "かわさき じゅん"
   :history [{:year 2016 :month 4 :description "◯◯大学 入学"}]})

(deftest a-minimal-form-is-valid
  (is (model/valid? base))
  (is (= base (model/rirekisho base))))

(deftest the-four-removed-fields-cannot-be-written
  (doseq [k model/removed-fields]
    (testing (str k " は 2021 様式例が設けなかった欄")
      (let [ps (model/problems (assoc base k "何か"))]
        (is (= [{:field k
                 :problem :field-removed-by-the-2021-form
                 :rationale "応募者のプライバシー性が高く、本人に責任のない事項"}]
               ps))
        (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                     (model/rirekisho (assoc base k "何か"))))))))

(deftest gender-is-free-text-and-may-be-absent
  (testing "選択肢を用意しない。未記載も正当"
    (is (model/valid? base))
    (is (model/valid? (assoc base :gender-note "ノンバイナリ")))
    (is (model/valid? (assoc base :gender-note "女")))))

(deftest name-and-kana-are-required
  (is (= [{:field :name :problem :required}]
         (model/problems (dissoc base :name))))
  (is (= [{:field :name-kana :problem :required}]
         (model/problems (assoc base :name-kana "   ")))))

(deftest history-entries-are-checked-per-row
  (testing "全部の問題を返す —— 1 つ目で止めない"
    (let [ps (model/problems
              (assoc base :history [{:year "2016" :month 13 :description ""}]))]
      (is (= #{:year-must-be-an-integer :month-must-be-1-to-12 :description-required}
             (into #{} (map :problem) ps)))
      (is (every? #(= 0 (:index %)) ps)))))

(deftest history-and-licenses-count-as-identifying
  (testing "「◯◯高校 / ◯年◯月入学」の組は氏名が無くても個人を特定しうる"
    (is (contains? model/identifying-fields :history))
    (is (contains? model/identifying-fields :licenses))))
