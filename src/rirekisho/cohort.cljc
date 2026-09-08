(ns rirekisho.cohort
  "**クエリできる面**。履歴書から非識別な射影だけを取り出し、datom として書く。

  ## なぜ2つの面に割れているのか

  暗号文は join できない。だから「サーバに読ませない」と「Datalog でクエリする」は
  同じ datom には同時に成立しない —— 片方を選ぶのではなく、**何をクエリしたいかを
  先に決めて、それだけを非識別な射影として平文で書く**。識別する中身は封緘して
  blob に置く(`rirekisho.envelope`)。

  この分割は kagi(index/ACL は Datomic graph、secret は SealedBlockStore)と
  etzhayyim の talent(cohort は graph、PII は ciphertext)が既に採っている形で、
  ここが3つ目の実装になる。

  ## k-匿名

  cohort は **count が k 以上の時だけ公開する**。1人しかいない cohort の
  「ISCO-2512 / JPN / senior」は、その1人を名指ししているのと変わらない。
  `publishable?` を通らない cohort は datom を出さない —— 「出すけど数を丸める」
  のではなく出さない。丸めた数は差分攻撃で戻せる。

  cohort は **メンバー一覧を持たない**。誰が属するかを持った時点で k-匿名は
  意味を失う。属性は count と集計だけ。"
  (:require [kotoba.lang.text :as str]))

(def default-k
  "公開に要する最小人数。5 は「小さすぎない」下限であって、これで十分という値では
  ない —— 母集団が偏った軸(希少職種 × 小国)では k を上げる必要がある。"
  5)

(def cohort-attributes
  "cohort 面に載ってよい属性。ここに無いものは書かない。"
  #{:cohort/isco :cohort/country :cohort/seniority :cohort/skill :cohort/count})

(defn project
  "履歴書 → cohort 射影。**識別項目は 1 つも通さない。**

  返すのは cohort の *鍵* と、その人が寄与する集計素材だけ。氏名・生年月日・住所・
  写真・学歴の記述は入り口で捨てる(そもそも引数から読まない)。"
  [{:keys [isco country seniority skills]}]
  (cond-> {}
    isco      (assoc :cohort/isco isco)
    country   (assoc :cohort/country country)
    seniority (assoc :cohort/seniority seniority)
    (seq skills) (assoc :cohort/skill (set skills))))

(defn leaks
  "cohort 射影に混ざってしまった非 cohort 属性を返す。空なら安全。

  射影を手で組み立てた場合や、上流の schema が増えた場合に効く。`project` は
  そもそも識別項目を読まないが、この関数は **書き込み直前の最後の関門**として
  誰が作った射影にも適用できる。"
  [projection]
  (into #{} (remove cohort-attributes) (keys projection)))

(defn publishable?
  "この cohort を公開してよいか。count が k 以上で、かつ漏れが無いこと。"
  ([cohort] (publishable? cohort default-k))
  ([cohort k]
   (and (empty? (leaks cohort))
        (int? (:cohort/count cohort))
        (>= (:cohort/count cohort) k))))

(defn- cohort-key [{:keys [:cohort/isco :cohort/country :cohort/seniority]}]
  (str/join "|" [(or isco "") (or country "") (or seniority "")]))

(defn aggregate
  "射影の集まり → 公開可能な cohort の集まり。

  k 未満の cohort は**返さない**。何件落としたかは `:suppressed` で返す ——
  黙って減らすと、呼び出し側が「全部載っている」と読める。"
  ([projections] (aggregate projections default-k))
  ([projections k]
   (let [grouped (group-by cohort-key projections)
         built (for [[_ members] grouped
                     :let [head (first members)]]
                 (cond-> {:cohort/count (count members)}
                   (:cohort/isco head)      (assoc :cohort/isco (:cohort/isco head))
                   (:cohort/country head)   (assoc :cohort/country (:cohort/country head))
                   (:cohort/seniority head) (assoc :cohort/seniority (:cohort/seniority head))
                   true (assoc :cohort/skill
                               (into #{} (mapcat :cohort/skill) members))))
         {publish true suppress false} (group-by #(publishable? % k) built)]
     {:cohorts (vec publish)
      :suppressed (count suppress)
      :k k})))
