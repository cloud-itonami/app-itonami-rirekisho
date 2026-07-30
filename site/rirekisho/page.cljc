(ns rirekisho.page
  "`itonami.cloud/cloud-itonami/cloud-itonami-rirekisho/` の SSR マークアップ。

  UI は **デジタル庁デザインシステム(DADS)** —— `kotoba-lang/jp-go-digital-design-system`
  の cljc hiccup 版を使う。この道具が作るのは厚生労働省の様式例に沿った日本の
  公的書類なので、Apple HIG 系(`kotoba-uiux`)ではなく日本の行政の design system に
  載せる方が読み手の期待に合う。

  ## このページは何も送信しない

  履歴書は氏名・生年月日・住所を含む。**入力は 1 バイトもネットワークに出ない** ——
  サーバも無く、fetch も無く、localStorage も使わない(閉じれば消える)。それを成立させる
  ために外部リクエストもゼロにしてある: DADS の CSS は inline、scittle は同一オリジンに
  vendor 済みで CDN を叩かない。プライバシーを謳う道具が第三者 CDN に読み込みを
  出していたら、その CDN は『誰がいつこのページを開いたか』を知る。

  ## 検証は同じ .cljc

  「2021 様式例が設けなかった 4 欄は書き込めない」という不変条件は
  `rirekisho.model` が持っており、ブラウザではその **同じファイル**を scittle が読む。
  JS に書き直すと検証が 2 実装になり、片方だけ直された瞬間に嘘になる
  (inkan が組版で同じ判断をしている — ADR-2607301300 §6)。"
  (:require [jp-go-dds.core :as dds]
            [jp-go-dds.page :as dds-page]))

(def ^:private site-title "履歴書をつくる — 厚生労働省 様式例(2021)")

(def ^:private site-description
  "厚生労働省「新たな履歴書の様式例」(2021-04-16)に沿った履歴書をブラウザだけで作成・印刷します。入力は送信されません。")

;; ───────── 入力欄 ─────────

(defn- field
  [id label {:keys [support required? textarea? rows placeholder]}]
  (dds/form-field
   (cond-> {:label label :for id}
     support (assoc :support support)
     required? (assoc :requirement "必須" :required? true)
     (and (not required?) (not textarea?)) (assoc :requirement "任意" :required? false))
   (if textarea?
     (dds/textarea (cond-> {:id id :rows (or rows 4)}
                     placeholder (assoc :placeholder placeholder)))
     (dds/input-text (cond-> {:id id}
                       placeholder (assoc :placeholder placeholder))))))

(defn- basic-section []
  (dds/section
   {:title "基本情報" :id "basic"}
   (dds/grid
    {:min "16rem"}
    (field "name" "氏名" {:required? true :placeholder "川崎 純"})
    (field "name-kana" "ふりがな" {:required? true :placeholder "かわさき じゅん"})
    (field "date-of-birth" "生年月日" {:placeholder "1990-04-01"})
    (field "gender-note" "性別"
           {:support "自由記述の任意欄です。2021 年の様式例で〔男・女〕の選択ではなくなりました。書かなくても構いません。"})
    (field "address-kana" "現住所 ふりがな" {})
    (field "address" "現住所" {})
    (field "phone" "電話番号" {})
    (field "email" "メールアドレス" {}))))

(defn- history-row [i]
  [:div {:class "rk-history-row" :data-index (str i)}
   (dds/input-text {:id (str "history-year-" i) :placeholder "年"
                    :inputmode "numeric" :aria-label (str (inc i) "行目 年")})
   (dds/input-text {:id (str "history-month-" i) :placeholder "月"
                    :inputmode "numeric" :aria-label (str (inc i) "行目 月")})
   (dds/input-text {:id (str "history-desc-" i) :placeholder "○○大学 ○○学部 入学"
                    :aria-label (str (inc i) "行目 事項")})])

(defn- history-section []
  (dds/section
   {:title "学歴・職歴" :id "history"}
   [:p {:class "rk-note"} "年・月・事項の 3 列です。行が足りなければ追加してください。"]
   (into [:div {:id "history-rows"}] (map history-row (range 6)))
   (dds/button "行を追加" {:type :outline :id "add-history-row"})))

(defn- free-text-section []
  (dds/section
   {:title "資格・志望動機など" :id "free-text"}
   (field "licenses" "免許・資格"
          {:textarea? true :rows 3 :placeholder "2020年3月 普通自動車第一種運転免許 取得"})
   (field "motivation" "志望の動機、特技、好きな学科、アピールポイントなど"
          {:textarea? true :rows 5})
   (field "self-pr" "自己PR" {:textarea? true :rows 5})
   (field "requests" "本人希望記入欄"
          {:textarea? true :rows 3
           :support "給与・職種・勤務時間・勤務地など、希望があれば記入します。"})))

;; ───────── 職務経歴書 ─────────

(defn- shokureki-entry [i]
  [:div {:class "rk-entry" :data-index (str i)}
   (dds/form-field {:label "会社・組織名" :for (str "sk-org-" i)}
                   (dds/input-text {:id (str "sk-org-" i) :placeholder "株式会社○○"}))
   [:div {:class "rk-entry-period"}
    (dds/form-field {:label "在籍開始" :for (str "sk-from-" i)}
                    (dds/input-text {:id (str "sk-from-" i) :placeholder "2020-04"}))
    (dds/form-field {:label "在籍終了" :for (str "sk-to-" i)
                     :support "在職中なら空欄"}
                    (dds/input-text {:id (str "sk-to-" i) :placeholder "2024-03"}))]
   (dds/form-field {:label "担当業務（1 行に 1 つ）" :for (str "sk-duties-" i)}
                   (dds/textarea {:id (str "sk-duties-" i) :rows 3
                                  :placeholder "決済基盤の設計\nチームリード"}))])

(defn- shokureki-section []
  (dds/section
   {:title "職務経歴書" :id "shokureki"}
   [:p {:class "rk-note"}
    "職務経歴書には "
    [:strong "公的な様式がありません"]
    "。A4 1〜2 枚という慣行と、3 つの構成の型があるだけです。"
    "型を選ぶと、並び順はその型のとおりに整えます。"]
   (dds/form-field
    {:label "構成の型" :for "sk-style"}
    (dds/select {:id "sk-style"}
                [["reverse-chronological" "逆編年式（新しい順）"]
                 ["chronological" "編年式（古い順）"]
                 ["functional" "キャリア式（職務内容ごと）"]]))
   (field "sk-summary" "職務要約"
          {:textarea? true :rows 3
           :support "冒頭の要約が無いと、読み手は本文を読むまで何の人か分かりません。"})
   (into [:div {:id "sk-entries"}] (map shokureki-entry (range 2)))
   (dds/button "職歴を追加" {:type :outline :id "sk-add-entry"})))

;; ───────── 様式の説明 ─────────

(defn- removed-fields-section []
  (dds/section
   {:title "この様式に無い欄" :id "removed"}
   [:p {:class "rk-note"}
    "2021 年の様式例は、次の 4 欄を "
    [:strong "設けないこと"]
    " にしました。応募者のプライバシー性が高く、本人に責任のない事項に当たるためです。"
    "この道具はそれらを空欄にするのではなく、"
    [:strong "そもそも入力欄を持ちません"] "。"]
   (dds/table
    {:caption "2021 年の様式例が設けなかった欄"
     :headers ["欄" "設けなかった理由"]
     :rows [["通勤時間" "応募者のプライバシー性が非常に高い"]
            ["扶養家族数（配偶者を除く）" "応募者のプライバシー性が非常に高い"]
            ["配偶者" "応募者のプライバシー性が非常に高い"]
            ["配偶者の扶養義務" "応募者のプライバシー性が非常に高い"]]})
   [:p {:class "rk-note"}
    "履歴書の様式例は JIS Z 8303 から 2020 年 7 月の改正で削除されました。"
    "「JIS 規格の履歴書」はもう存在しません。"]))

;; ───────── 文書 ─────────

(defn- privacy-banner []
  (dds/notification-banner
   {:type :info-1 :heading "入力は送信されません"}
   [:p "この履歴書はあなたのブラウザの中だけで組み立てられます。サーバはなく、"
    "送信も保存もしません（ページを閉じると消えます）。外部への読み込みもゼロです。"]))

(defn body []
  [:div {:class "rk-app"}
   [:header {:class "rk-header"}
    (dds/container
     (dds/heading 1 "履歴書をつくる" {:size "45"})
     [:p {:class "rk-lead"}
      "厚生労働省「新たな履歴書の様式例」(2021 年 4 月 16 日) に沿った履歴書を作り、"
      "そのまま印刷または PDF 保存できます。"]
     (privacy-banner))]

   [:main {:class "rk-main"}
    (dds/container
     [:div {:class "rk-columns"}
      [:form {:id "rirekisho-form" :class "rk-form"}
       (basic-section)
       (history-section)
       (free-text-section)
       (shokureki-section)
       (removed-fields-section)]

      [:div {:class "rk-preview-pane"}
       (dds/heading 2 "プレビュー" {:size "32"})
       [:div {:id "problems" :class "rk-problems" :role "status" :aria-live "polite"}]
       [:div {:id "preview" :class "rk-preview"}]
       [:div {:id "sk-preview" :class "rk-preview"}]
       (dds/button "印刷 / PDF 保存" {:type :solid-fill :size "lg" :id "print"})
       [:p {:class "rk-note"}
        "印刷ダイアログで「PDF に保存」を選ぶと PDF になります。用紙は A4 です。"]]])]

   [:footer {:class "rk-footer"}
    (dds/container
     [:p "UI は "
      [:a {:href "https://www.digital.go.jp/policies/servicedesign/designsystem"}
       "デジタル庁デザインシステム"]
      "（MIT, © デジタル庁）。様式は厚生労働省の様式例に基づきます。"]
     [:p "実装: "
      [:a {:href "https://github.com/cloud-itonami/cloud-itonami-rirekisho"}
       "cloud-itonami/cloud-itonami-rirekisho"]
      "（AGPL-3.0-or-later）。検証はこのページが読み込む "
      [:code "rirekisho.model"] " そのものです。"])]])

(def app-css
  "アプリ固有の CSS。色は DADS の custom property から取り、raw hex は帳票の罫線だけに使う
  （帳票のレイアウトは上流 DADS に無いので、そこだけは自前で持つ）。"
  "
.rk-header { padding: 2.5rem 0 1.5rem; border-bottom: 1px solid var(--color-border-divider, #d8d8d8); }
.rk-lead { margin: .75rem 0 1.25rem; max-width: 42em; }
.rk-main { padding: 2rem 0 4rem; }
.rk-columns { display: grid; grid-template-columns: minmax(0,1fr); gap: 2.5rem; }
@media (min-width: 60rem) { .rk-columns { grid-template-columns: minmax(0,1fr) minmax(0,1fr); align-items: start; } }
.rk-preview-pane { position: sticky; top: 1rem; }
.rk-note { font-size: .875rem; max-width: 46em; }
.rk-entry { border: 1px solid var(--color-border-divider, #d8d8d8); padding: 1rem; margin-bottom: 1rem; }
.rk-entry-period { display: grid; grid-template-columns: 1fr 1fr; gap: .75rem; }
.rk-history-row { display: grid; grid-template-columns: 5rem 4rem minmax(0,1fr); gap: .5rem; margin-bottom: .5rem; }
.rk-problems:not(:empty) { margin: .75rem 0; padding: .75rem 1rem; border-left: 4px solid var(--color-error-1, #ec0000); background: #fff5f5; }
.rk-problems ul { margin: 0; padding-left: 1.25rem; }
.rk-preview { border: 1px solid var(--color-border-field, #767676); padding: 1.25rem; margin: 1rem 0; background: #fff; }

.rk-doc { font-size: .8125rem; line-height: 1.5; }
.rk-doc h3 { font-size: 1.125rem; margin: 0 0 .75rem; }
.rk-doc table { width: 100%; border-collapse: collapse; margin-bottom: 1rem; }
.rk-doc th, .rk-doc td { border: 1px solid #333; padding: .375rem .5rem; text-align: left; vertical-align: top; }
.rk-doc th { width: 8em; font-weight: 500; background: #f4f4f4; }
.rk-doc .rk-doc-year { width: 4em; text-align: center; }
.rk-doc .rk-doc-month { width: 3em; text-align: center; }
.rk-doc .rk-doc-empty { color: #767676; }

@media print {
  @page { size: A4; margin: 14mm; }
  .rk-header, .rk-form, .rk-footer, .rk-problems, #print,
  .rk-preview-pane > h2, .rk-preview-pane > .rk-note { display: none !important; }
  .rk-preview { border: 0; padding: 0; margin: 0; }
  #sk-preview { break-before: page; page-break-before: always; }
  .rk-main { padding: 0; }
  .rk-columns { display: block; }
  .rk-preview-pane { position: static; }
}
")

(defn document
  "完全な HTML 文書。`css` は vendored dds.css の中身
  (呼び出し側が読む —— `jp-go-dds.page` は I/O を持たない純関数)。"
  [{:keys [css]}]
  (dds-page/->page
   {:title site-title
    :description site-description
    :css css
    :app-css app-css
    ;; scittle は同一オリジンから読む。CDN にすると、プライバシーを謳うページが
    ;; 第三者に「誰がいつ開いたか」を渡すことになる。
    :head [[:script {:src "./scittle.js"}]
           [:script {:type "application/x-scittle" :src "./rirekisho/model.cljs"}]
           [:script {:type "application/x-scittle" :src "./rirekisho/shokureki.cljs"}]
           [:script {:type "application/x-scittle" :src "./app.cljs"}]]}
   (body)))
