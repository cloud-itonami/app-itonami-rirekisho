(ns rirekisho.model
  "履歴書の値モデル —— 厚生労働省「新たな履歴書の様式例」(2021-04-16) に従う。

  ## なぜ JIS ではないのか

  履歴書の様式例は **JIS Z 8303 から 2020-07 の改正で削除された**。きっかけは
  LGBT 当事者団体による性別欄削除の要請で、翌 2021-04-16 に厚生労働省が
  差し替えとなる様式例を公表した。「JIS 規格の履歴書」はもう存在しないので、
  この ns は厚労省様式例だけを正本とする。

  ## 差別的項目は「任意」ではなく **表現できない**

  2021 様式例は次の 4 欄を、応募者のプライバシー性が高く本人に責任のない事項に
  当たるとして **設けないこと**にした:

      通勤時間 / 扶養家族数(配偶者を除く) / 配偶者 / 配偶者の扶養義務

  この ns はそれを「既定で空欄」にするのではなく、**書き込もうとすると refuse する**
  (`removed-fields`)。UI の初期値を消すだけの実装は、テンプレートを1つ足せば復活して
  しまう —— 構造として持てないようにするのが様式例の趣旨に合う。

  性別は `[男・女]` の選択ではなく **自由記述の任意欄**(`:gender-note`)。列挙型に
  しないのは、性自認の多様な在り方に対応するという変更理由そのものが「選択肢を
  用意しない」ことだったため。未記載も正当な状態なので `nil` を許す。"
  (:require [kotoba.lang.text :as str]))

(def spec-source
  {:issuer "厚生労働省"
   :title "新たな履歴書の様式例"
   :published "2021-04-16"
   :supersedes "JIS Z 8303 の履歴書様式例(2020-07 の改正で削除)"})

(def removed-fields
  "2021 様式例が意図的に設けなかった欄。書き込みは refuse する。"
  #{:commute-time :dependents :spouse :spouse-support-obligation})

(def identifying-fields
  "封緘の対象。開示先ごとの grant が無い限り、この面は平文で外に出さない。

  学歴・職歴の記述(`:history`)と資格(`:licenses`)もここに入る —— 「◯◯高校」と
  「◯年◯月入学」の組は、氏名が無くても個人を特定しうるため。cohort 面へ渡すのは
  ここから導出した非識別な射影だけ(`rirekisho.cohort`)。"
  #{:name :name-kana :date-of-birth :address :address-kana :phone :email
    :gender-note :photo :history :licenses :motivation :self-pr :requests})

(defn- blank? [v]
  (or (nil? v) (and (string? v) (str/blank? v))))

(defn history-entry
  "学歴・職歴の 1 行。様式例は年・月・事項の 3 列。"
  [{:keys [year month description]}]
  {:year year :month month :description description})

(defn- entry-problems [{:keys [year month description]} idx]
  (cond-> []
    (not (int? year))
    (conj {:field :history :index idx :problem :year-must-be-an-integer :value year})

    (not (and (int? month) (<= 1 month 12)))
    (conj {:field :history :index idx :problem :month-must-be-1-to-12 :value month})

    (blank? description)
    (conj {:field :history :index idx :problem :description-required})))

(defn problems
  "履歴書として成立しない点を列挙する。空 vector なら valid。

  「1つ目の問題で throw」ではなく全部返すのは、これがフォームの背後に立つ値だから
  —— 使う側は1回の検証で全部の欄に印を付けたい。"
  [rirekisho]
  (let [h (:history rirekisho)]
    (vec
     (concat
      (for [k (keys rirekisho)
            :when (contains? removed-fields k)]
        {:field k
         :problem :field-removed-by-the-2021-form
         :rationale "応募者のプライバシー性が高く、本人に責任のない事項"})
      (for [k [:name :name-kana]
            :when (blank? (get rirekisho k))]
        {:field k :problem :required})
      (when (and (some? h) (not (sequential? h)))
        [{:field :history :problem :must-be-a-sequence}])
      (when (sequential? h)
        (mapcat entry-problems h (range)))))))

(defn valid? [rirekisho] (empty? (problems rirekisho)))

(defn rirekisho
  "履歴書を構築する。成立しない場合は throw(問題は ex-data の `:problems`)。"
  [m]
  (let [ps (problems m)]
    (when (seq ps)
      (throw (ex-info "この履歴書は 2021 様式例として成立しない" {:problems ps})))
    m))
