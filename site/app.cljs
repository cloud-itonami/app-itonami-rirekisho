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
            [rirekisho.model :as model]))

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
            (set! (.-textContent li) (problem-text p))
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

(defn refresh! []
  (let [r (collect)]
    ;; model/problems は **全部**返す(1 つ目で止めない)ので、一度の入力で
    ;; すべての欄に印を付けられる。
    (render-problems! (model/problems r))
    (render-preview! r)))

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

(defn init! []
  (.addEventListener (.getElementById js/document "rirekisho-form") "input"
                     (fn [_] (refresh!)))
  (.addEventListener (.getElementById js/document "add-history-row") "click"
                     (fn [_] (add-history-row!)))
  (.addEventListener (.getElementById js/document "print") "click"
                     (fn [_] (.print js/window)))
  (refresh!))

(if (= "loading" (.-readyState js/document))
  (.addEventListener js/document "DOMContentLoaded" (fn [_] (init!)))
  (init!))
