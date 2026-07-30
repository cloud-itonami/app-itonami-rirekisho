;; ブラウザ側。scittle が読む。
;;
;; **検証は書き直さない** —— `rirekisho.model/problems` をそのまま呼ぶ。同じ ns が
;; JVM のテスト(28 tests)とこのページの両方で動くので、「2021 様式例が設けなかった
;; 4 欄は refuse する」という不変条件が UI 側だけ古くなることが起きない。
;;
;; ネットワークは一切使わない。fetch も XHR も localStorage も無い —— 履歴書は
;; 氏名・生年月日・住所を含むので、この画面から出さないことがこの道具の要件そのもの。

;; **`ns` フォームで宣言する。** scittle は各 `<script type="application/x-scittle">` を
;; 同じ SCI コンテキストで順に評価するので、ns を宣言しないと直前のスクリプトの ns
;; (ここでは `rirekisho.model`) のまま評価され、裸の `require` で入れた別名が
;; 解決できない。実測: `Could not resolve symbol: model/problems`。
(ns rirekisho.app
  (:require [clojure.string :as str]
            [rirekisho.model :as model]
            [rirekisho.shokureki :as shokureki]))

;; 職務経歴書側は下で定義するが、問題表示は 1 箇所にまとめたいので先に宣言する。
(declare sk-problem-text add-shokureki-entry!)

(defn- el [id] (.getElementById js/document id))

(defn- value [id]
  (some-> (el id) .-value str/trim))

(defn- blank->nil [s] (when-not (str/blank? s) s))

(defn- parse-int* [s]
  (let [n (js/parseInt s 10)]
    (when-not (js/isNaN n) n)))

(defn- history-rows []
  (let [rows (.querySelectorAll js/document ".rk-history-row")]
    (->> (range (.-length rows))
         (map (fn [i]
                (let [idx (.getAttribute (.item rows i) "data-index")
                      y (value (str "history-year-" idx))
                      m (value (str "history-month-" idx))
                      d (value (str "history-desc-" idx))]
                  (when-not (and (str/blank? (or y "")) (str/blank? (or m ""))
                                 (str/blank? (or d "")))
                    ;; 数値にできない年月は **nil のまま model へ渡す** —— ここで
                    ;; 握り潰すと model の検証が「空欄」と区別できなくなる。
                    {:year (parse-int* y) :month (parse-int* m) :description d}))))
         (remove nil?)
         vec)))

(defn- collect []
  (let [h (history-rows)]
    (cond-> {:name (blank->nil (value "name"))
             :name-kana (blank->nil (value "name-kana"))}
      (blank->nil (value "date-of-birth")) (assoc :date-of-birth (value "date-of-birth"))
      (blank->nil (value "gender-note")) (assoc :gender-note (value "gender-note"))
      (blank->nil (value "address")) (assoc :address (value "address"))
      (blank->nil (value "address-kana")) (assoc :address-kana (value "address-kana"))
      (blank->nil (value "phone")) (assoc :phone (value "phone"))
      (blank->nil (value "email")) (assoc :email (value "email"))
      (blank->nil (value "licenses")) (assoc :licenses (value "licenses"))
      (blank->nil (value "motivation")) (assoc :motivation (value "motivation"))
      (blank->nil (value "self-pr")) (assoc :self-pr (value "self-pr"))
      (blank->nil (value "requests")) (assoc :requests (value "requests"))
      (seq h) (assoc :history h))))

;; ───────── 問題の表示 ─────────

(def ^:private field-labels
  {:name "氏名" :name-kana "ふりがな" :history "学歴・職歴"
   :date-of-birth "生年月日" :address "現住所"})

(def ^:private problem-messages
  {:required "入力してください"
   :year-must-be-an-integer "年は数字で入力してください"
   :month-must-be-1-to-12 "月は 1〜12 で入力してください"
   :description-required "事項を入力してください"
   :must-be-a-sequence "行の形式が不正です"
   :field-removed-by-the-2021-form "2021 年の様式例が設けなかった欄です"})

(defn- problem-text [{:keys [field problem index]}]
  (str (get field-labels field (name field))
       (when index (str " " (inc index) "行目"))
       ": " (get problem-messages problem (name problem))))

(defn- render-problems! [problems]
  (let [node (el "problems")]
    (set! (.-innerHTML node) "")
    (when (seq problems)
      (let [ul (.createElement js/document "ul")]
        (doseq [p problems]
          (let [li (.createElement js/document "li")]
            ;; textContent。innerHTML に入力由来の文字列を混ぜない。
            (set! (.-textContent li)
                  (if (::shokureki p) (sk-problem-text p) (problem-text p)))
            (.appendChild ul li)))
        (.appendChild node ul)))))

;; ───────── 帳票 ─────────

(defn- td [text {:keys [cls]}]
  (let [n (.createElement js/document "td")]
    (when cls (set! (.-className n) cls))
    (if (str/blank? (or text ""))
      (do (set! (.-textContent n) "—") (set! (.-className n) (str (or cls "") " rk-doc-empty")))
      (set! (.-textContent n) text))
    n))

(defn- kv-row! [tbody label v]
  (let [tr (.createElement js/document "tr")
        th (.createElement js/document "th")]
    (set! (.-textContent th) label)
    (.appendChild tr th)
    (.appendChild tr (td v {}))
    (.appendChild tbody tr)))

(defn- section-node [title]
  (let [h (.createElement js/document "h4")]
    (set! (.-textContent h) title)
    h))

(defn- render-preview! [r]
  (let [node (el "preview")
        doc (.createElement js/document "div")]
    (set! (.-className doc) "rk-doc")
    (set! (.-innerHTML node) "")

    (let [h3 (.createElement js/document "h3")]
      (set! (.-textContent h3) "履歴書")
      (.appendChild doc h3))

    ;; 基本情報
    (let [t (.createElement js/document "table")
          tb (.createElement js/document "tbody")]
      (kv-row! tb "ふりがな" (:name-kana r))
      (kv-row! tb "氏名" (:name r))
      (kv-row! tb "生年月日" (:date-of-birth r))
      (when (:gender-note r) (kv-row! tb "性別" (:gender-note r)))
      (kv-row! tb "現住所 ふりがな" (:address-kana r))
      (kv-row! tb "現住所" (:address r))
      (kv-row! tb "電話番号" (:phone r))
      (kv-row! tb "メールアドレス" (:email r))
      (.appendChild t tb)
      (.appendChild doc t))

    ;; 学歴・職歴
    (.appendChild doc (section-node "学歴・職歴"))
    (let [t (.createElement js/document "table")
          thead (.createElement js/document "thead")
          htr (.createElement js/document "tr")
          tb (.createElement js/document "tbody")]
      (doseq [[label cls] [["年" "rk-doc-year"] ["月" "rk-doc-month"] ["事項" nil]]]
        (let [th (.createElement js/document "th")]
          (set! (.-textContent th) label)
          (when cls (set! (.-className th) cls))
          (.appendChild htr th)))
      (.appendChild thead htr)
      (.appendChild t thead)
      (if (seq (:history r))
        (doseq [{:keys [year month description]} (:history r)]
          (let [tr (.createElement js/document "tr")]
            (.appendChild tr (td (some-> year str) {:cls "rk-doc-year"}))
            (.appendChild tr (td (some-> month str) {:cls "rk-doc-month"}))
            (.appendChild tr (td description {}))
            (.appendChild tb tr)))
        (let [tr (.createElement js/document "tr")
              c (.createElement js/document "td")]
          (set! (.-colSpan c) 3)
          (set! (.-className c) "rk-doc-empty")
          (set! (.-textContent c) "（未入力）")
          (.appendChild tr c)
          (.appendChild tb tr)))
      (.appendChild t tb)
      (.appendChild doc t))

    ;; 自由記述
    (doseq [[label k] [["免許・資格" :licenses]
                       ["志望の動機、特技、好きな学科、アピールポイントなど" :motivation]
                       ["自己PR" :self-pr]
                       ["本人希望記入欄" :requests]]]
      (.appendChild doc (section-node label))
      (let [p (.createElement js/document "p")]
        (if (get r k)
          (set! (.-textContent p) (get r k))
          (do (set! (.-className p) "rk-doc-empty")
              (set! (.-textContent p) "（未入力）")))
        (.appendChild doc p)))

    (.appendChild node doc)))

;; ───────── 反映 ─────────

;; ───────── 職務経歴書 ─────────

(defn- collect-shokureki []
  (let [nodes (.querySelectorAll js/document ".rk-entry")
        entries (->> (range (.-length nodes))
                     (map (fn [i]
                            (let [idx (.getAttribute (.item nodes i) "data-index")
                                  org (value (str "sk-org-" idx))
                                  from (value (str "sk-from-" idx))
                                  to (value (str "sk-to-" idx))
                                  duties (->> (str/split-lines (or (value (str "sk-duties-" idx)) ""))
                                              (map str/trim)
                                              (remove str/blank?)
                                              vec)]
                              (when-not (and (str/blank? (or org "")) (str/blank? (or from ""))
                                             (empty? duties))
                                {:organization org :from from
                                 :to (blank->nil to) :duties duties}))))
                     (remove nil?)
                     vec)]
    {:style (keyword (or (blank->nil (value "sk-style")) "reverse-chronological"))
     :summary (blank->nil (value "sk-summary"))
     :entries entries}))

(def ^:private sk-problem-messages
  {:required "入力してください"
   :must-be-one-of "構成の型を選んでください"
   :must-be-a-sequence "職歴の形式が不正です"
   :at-least-one-entry-required "職歴を 1 件以上入力してください"
   :organization-required "会社・組織名を入力してください"
   :at-least-one-duty-required "担当業務を 1 行以上入力してください"
   :from-must-be-yyyy-mm "在籍開始は YYYY-MM で入力してください"
   :to-must-be-yyyy-mm-or-blank "在籍終了は YYYY-MM か空欄にしてください"
   :from-is-after-to "在籍開始が終了より後になっています"})

(def ^:private sk-field-labels
  {:style "構成の型" :summary "職務要約" :entries "職歴"})

(defn- sk-problem-text [{:keys [field problem index]}]
  (str (get sk-field-labels field (name field))
       (when index (str " " (inc index) "件目"))
       ": " (get sk-problem-messages problem (name problem))))

(defn- render-shokureki! [sk]
  (let [node (el "sk-preview")
        doc (.createElement js/document "div")]
    (set! (.-innerHTML node) "")
    (set! (.-className doc) "rk-doc")
    (let [h3 (.createElement js/document "h3")]
      (set! (.-textContent h3) "職務経歴書")
      (.appendChild doc h3))
    (let [p (.createElement js/document "p")]
      (set! (.-className p) "rk-doc-style")
      (set! (.-textContent p)
            (str (get-in shokureki/styles [(:style sk) :label] "—")))
      (.appendChild doc p))
    (.appendChild doc (section-node "職務要約"))
    (let [p (.createElement js/document "p")]
      (if (:summary sk)
        (set! (.-textContent p) (:summary sk))
        (do (set! (.-className p) "rk-doc-empty")
            (set! (.-textContent p) "（未入力）")))
      (.appendChild doc p))
    (.appendChild doc (section-node "職務経歴"))
    ;; **並び順は shokureki/ordered-entries に決めさせる。** 入力順のまま出すと、
    ;; 型を宣言しているのに中身が従っていない書類になる。
    (let [entries (shokureki/ordered-entries sk)]
      (if (seq entries)
        (doseq [{:keys [organization from to duties]} entries]
          (let [h (.createElement js/document "h5")
                period (.createElement js/document "p")
                ul (.createElement js/document "ul")]
            (set! (.-textContent h) (or organization "—"))
            (set! (.-className period) "rk-doc-period")
            (set! (.-textContent period)
                  (str (or from "—") " 〜 " (or to "現在")))
            (.appendChild doc h)
            (.appendChild doc period)
            (doseq [d duties]
              (let [li (.createElement js/document "li")]
                (set! (.-textContent li) d)
                (.appendChild ul li)))
            (.appendChild doc ul)))
        (let [p (.createElement js/document "p")]
          (set! (.-className p) "rk-doc-empty")
          (set! (.-textContent p) "（未入力）")
          (.appendChild doc p))))
    (.appendChild node doc)))

(defn refresh! []
  (let [r (collect)]
    ;; model/problems は **全部**返す(1 つ目で止めない)ので、一度の入力で
    ;; すべての欄に印を付けられる。
    (let [sk (collect-shokureki)
          ;; 職務経歴書がまだ空（型だけ既定値）のうちは問題を出さない ——
          ;; 開いた瞬間に赤が出ると、書き始める前から間違っていると読める。
          sk-touched? (or (:summary sk) (seq (:entries sk)))]
      (render-problems! (concat (model/problems r)
                                (when sk-touched?
                                  (map #(assoc % ::shokureki true)
                                       (shokureki/problems sk)))))
      (render-preview! r)
      (render-shokureki! sk))))

(defn- add-history-row! []
  (let [rows (.getElementById js/document "history-rows")
        n (.-length (.querySelectorAll js/document ".rk-history-row"))
        div (.createElement js/document "div")]
    (set! (.-className div) "rk-history-row")
    (.setAttribute div "data-index" (str n))
    (doseq [[suffix ph label] [["year" "年" "年"] ["month" "月" "月"] ["desc" "○○入社" "事項"]]]
      (let [span (.createElement js/document "span")
            input (.createElement js/document "input")]
        (set! (.-className span) "dads-input-text")
        (set! (.-className input) "dads-input-text__input")
        (.setAttribute input "data-size" "md")
        (set! (.-type input) "text")
        (set! (.-id input) (str "history-" suffix "-" n))
        (set! (.-placeholder input) ph)
        (.setAttribute input "aria-label" (str (inc n) "行目 " label))
        (.appendChild span input)
        (.appendChild div span)))
    (.appendChild rows div)
    (refresh!)))

(defn- add-shokureki-entry! []
  (let [box (.getElementById js/document "sk-entries")
        n (.-length (.querySelectorAll js/document ".rk-entry"))
        div (.createElement js/document "div")]
    (set! (.-className div) "rk-entry")
    (.setAttribute div "data-index" (str n))
    (doseq [[suffix label ph] [["org" "会社・組織名" "株式会社○○"]
                               ["from" "在籍開始" "2020-04"]
                               ["to" "在籍終了（在職中なら空欄）" "2024-03"]]]
      (let [wrap (.createElement js/document "div")
            lab (.createElement js/document "label")
            span (.createElement js/document "span")
            input (.createElement js/document "input")
            id (str "sk-" suffix "-" n)]
        (set! (.-className wrap) "dads-form-control-label")
        (.setAttribute wrap "data-size" "md")
        (set! (.-className lab) "dads-form-control-label__label")
        (set! (.-htmlFor lab) id)
        (set! (.-textContent lab) label)
        (set! (.-className span) "dads-input-text")
        (set! (.-className input) "dads-input-text__input")
        (.setAttribute input "data-size" "md")
        (set! (.-type input) "text")
        (set! (.-id input) id)
        (set! (.-placeholder input) ph)
        (.appendChild span input)
        (.appendChild wrap lab)
        (.appendChild wrap span)
        (.appendChild div wrap)))
    (let [wrap (.createElement js/document "div")
          lab (.createElement js/document "label")
          span (.createElement js/document "span")
          ta (.createElement js/document "textarea")
          id (str "sk-duties-" n)]
      (set! (.-className wrap) "dads-form-control-label")
      (set! (.-className lab) "dads-form-control-label__label")
      (set! (.-htmlFor lab) id)
      (set! (.-textContent lab) "担当業務（1 行に 1 つ）")
      (set! (.-className span) "dads-textarea")
      (set! (.-className ta) "dads-textarea__textarea")
      (set! (.-rows ta) 3)
      (set! (.-id ta) id)
      (.appendChild span ta)
      (.appendChild wrap lab)
      (.appendChild wrap span)
      (.appendChild div wrap))
    (.appendChild box div)
    (refresh!)))

(defn init! []
  (.addEventListener (.getElementById js/document "rirekisho-form") "input"
                     (fn [_] (refresh!)))
  (.addEventListener (.getElementById js/document "add-history-row") "click"
                     (fn [_] (add-history-row!)))
  (.addEventListener (.getElementById js/document "rirekisho-form") "change"
                     (fn [_] (refresh!)))
  (.addEventListener (.getElementById js/document "sk-add-entry") "click"
                     (fn [_] (add-shokureki-entry!)))
  (.addEventListener (.getElementById js/document "print") "click"
                     (fn [_] (.print js/window)))
  (refresh!))

(if (= "loading" (.-readyState js/document))
  (.addEventListener js/document "DOMContentLoaded" (fn [_] (init!)))
  (init!))
