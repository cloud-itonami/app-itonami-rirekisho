(ns rirekisho.shokureki
  "職務経歴書の値モデル。

  ## 履歴書と何が違うのか

  履歴書には様式がある（厚労省 2021 様式例）。**職務経歴書には無い。** 公的機関が
  定めた様式は存在せず、A4 1〜2 枚という慣行と、3 つの構成の型があるだけ。だから
  この ns は「正しい様式」を強制せず、**3 つの型のどれかを選ばせて、その型が要求する
  ものだけを検査する**。

  ## 3 つの型

  | 型 | 並び | 効くところ |
  |----|------|-----------|
  | `:chronological`（編年式） | 古い順 | 在籍が長く、積み上げを見せたい |
  | `:reverse-chronological`（逆編年式） | 新しい順 | 直近の経験が応募先に最も近い |
  | `:functional`（キャリア式） | 職務内容ごと | 転職回数が多い / 職種が一貫している |

  **並び順はこの ns が保証する。** 呼び出し側が並べた順を信じると、`:reverse-chronological`
  を選んだのに古い順のまま出力される——読み手には型の宣言しか見えないので、
  食い違っても誰も気づかない。

  ## 履歴書と同じ規律

  - 問題は**全部**返す（1 つ目で止めない）。フォームの背後に立つ値なので。
  - ここは識別情報を持つ面。cohort へ出すのは `rirekisho.cohort` の射影だけで、
    会社名も担当業務もそちらへは渡らない（在籍企業名は個人を特定しうる）。"
  (:require [kotoba.lang.text :as str]))

(def styles
  {:chronological         {:label "編年式" :order :ascending}
   :reverse-chronological {:label "逆編年式" :order :descending}
   :functional            {:label "キャリア式" :order :none}})

(def identifying-fields
  "封緘の対象。会社名・部署・担当業務は、氏名が無くても人を特定しうる。"
  #{:summary :entries :skills-note})

(defn- blank? [v] (or (nil? v) (and (string? v) (str/blank? v))))

(defn- ym->comparable
  "\"2020-04\" / \"2020\" → 比較できる整数。解釈できなければ nil。"
  [s]
  (when (string? s)
    (let [[y m] (str/split (str/trim s) #"-")
          y (parse-long (or y ""))
          m (parse-long (or m "1"))]
      (when (and y (<= 1900 y 2200) m (<= 1 m 12))
        (+ (* 12 y) m)))))

(defn- entry-problems
  [{:keys [organization from to duties]} idx style]
  (cond-> []
    (blank? organization)
    (conj {:field :entries :index idx :problem :organization-required})

    ;; キャリア式は「いつ」より「何を」なので期間を必須にしない。
    ;; 編年式・逆編年式は時系列で読ませる型なので、期間が無いと型が成立しない。
    (and (not= style :functional) (nil? (ym->comparable from)))
    (conj {:field :entries :index idx :problem :from-must-be-yyyy-mm :value from})

    ;; :to は空でよい（在職中）。書いてあるなら解釈できること。
    (and (some? to) (not (blank? to)) (nil? (ym->comparable to)))
    (conj {:field :entries :index idx :problem :to-must-be-yyyy-mm-or-blank :value to})

    (and (ym->comparable from) (ym->comparable to)
         (> (ym->comparable from) (ym->comparable to)))
    (conj {:field :entries :index idx :problem :from-is-after-to})

    (empty? (remove blank? (or duties [])))
    (conj {:field :entries :index idx :problem :at-least-one-duty-required})))

(defn problems
  "職務経歴書として成立しない点を列挙する。空 vector なら valid。"
  [{:keys [style entries summary] :as _shokureki}]
  (vec
   (concat
    (when-not (contains? styles style)
      [{:field :style :problem :must-be-one-of :allowed (set (keys styles))}])
    (when (blank? summary)
      [{:field :summary :problem :required
        :rationale "冒頭の職務要約が無いと、読み手は本文を読むまで何の人か分からない"}])
    (when-not (sequential? entries)
      [{:field :entries :problem :must-be-a-sequence}])
    (when (and (sequential? entries) (empty? entries))
      [{:field :entries :problem :at-least-one-entry-required}])
    (when (sequential? entries)
      (mapcat #(entry-problems %1 %2 style) entries (range))))))

(defn valid? [s] (empty? (problems s)))

(defn ordered-entries
  "宣言した型のとおりに並べ直した entries。

  **呼び出し側が並べた順は信じない。** 型は読み手への約束なので、宣言と中身が
  食い違ったら宣言の方に合わせる。`:functional` は時系列で並べない型なので
  入力順を保つ（職務内容のまとまりの順序は書き手が決めるもの）。"
  [{:keys [style entries]}]
  (let [order (get-in styles [style :order])]
    (case order
      :ascending  (vec (sort-by #(or (ym->comparable (:from %)) 0) entries))
      :descending (vec (sort-by #(or (ym->comparable (:from %)) 0) #(compare %2 %1) entries))
      (vec entries))))

(defn shokureki
  "職務経歴書を構築する。成立しなければ throw。**entries は型どおりに並べて返す。**"
  [m]
  (let [ps (problems m)]
    (when (seq ps)
      (throw (ex-info "この職務経歴書は成立しない" {:problems ps})))
    (assoc m :entries (ordered-entries m))))

(defn duration-months
  "1 社の在籍月数。`:to` が空なら `now-ym`（\"2026-07\"）までを在職中として数える。
  解釈できない期間は nil —— 0 を返すと『在籍していない』と読めてしまう。"
  [{:keys [from to]} now-ym]
  (let [a (ym->comparable from)
        b (or (ym->comparable to) (ym->comparable now-ym))]
    (when (and a b (<= a b)) (inc (- b a)))))

(defn total-months
  "合計在籍月数。**期間が読めない entry は数えない。** 読めなかった件数を併せて返す
  ——黙って落とすと、合計が実際より短いことに誰も気づかない。"
  [{:keys [entries]} now-ym]
  (let [ms (map #(duration-months % now-ym) entries)]
    {:months (reduce + 0 (remove nil? ms))
     :uncounted (count (filter nil? ms))}))
