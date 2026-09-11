(ns rirekisho.disclosure
  "**同意 = capability**。誰に・どの欄を・いつまで・何のために開示するかを、
  保有者が自分の鍵で刻んだ CACAO に落とす。

  ## なぜ VC ではなく CACAO なのか

  この workspace に W3C Verifiable Credentials の実装は無い(`org-w3-did` はあるが
  VC data model / SD-JWT / BBS+ の選択的開示は無い)。一方 `org-chainagnostic-cacao`
  は実装済みで、**保有者自身のランタイムで mint** でき、`resources` に権限を書け、
  期限を持つ。VC でやりたかったこと(自己主権的で、検証者がサーバに問い合わせずに
  検証でき、必要な項目だけ出す)は CACAO の resources で満たせる。

  ## 暗号は失効を与えない

  一度渡した平文は取り返せない。CACAO の期限が切れても、相手の手元にある復号済みの
  履歴書は消えない。だから `revoke` が止められるのは **これから先の開示**だけ ——
  この ns はそれを言葉で誤魔化さず、`revoke` の戻り値に `:already-disclosed` として
  明示する。「失効しました」と表示して既に渡ったものが消えたと読ませない。

  ## 失効は hard retract

  talent(ADR-0018) と同じく、フラグを立てるのではなく datom を retract する。
  `:disclosure/revoked? true` を足す実装は、フラグを読み落とした 1 つのクエリが
  そのまま漏洩になる。"
  (:require [clojure.set :as set]
            [rirekisho.model :as model]))

(def max-lifetime-days
  "1 つの grant が持てる最長期間。無期限の開示は同意ではなく譲渡なので許さない。"
  90)

(defn- iso->epoch-day [s]
  #?(:clj (.toEpochDay (java.time.LocalDate/parse s))
     :cljs (js/Math.floor (/ (.parse js/Date s) 86400000))))

(defn problems
  "grant として成立しない点を列挙する。"
  [{:keys [holder recipient fields purpose issued-on expires-on]}]
  (cond-> []
    (not (string? holder))    (conj {:field :holder :problem :required})
    (not (string? recipient)) (conj {:field :recipient :problem :required})
    (not (string? purpose))   (conj {:field :purpose
                                     :problem :required
                                     :rationale "何のための開示かを書かない grant は、後で範囲を争えない"})
    (not (set? fields))       (conj {:field :fields :problem :must-be-a-set})

    (and (set? fields) (empty? fields))
    (conj {:field :fields :problem :must-disclose-at-least-one-field})

    (and (set? fields) (seq (set/intersection fields model/removed-fields)))
    (conj {:field :fields
           :problem :field-removed-by-the-2021-form
           :offending (set/intersection fields model/removed-fields)})

    (and (set? fields)
         (seq (set/difference fields model/identifying-fields model/removed-fields)))
    (conj {:field :fields
           :problem :unknown-field
           :offending (set/difference fields model/identifying-fields model/removed-fields)})

    (not (and (string? issued-on) (string? expires-on)))
    (conj {:field :expires-on
           :problem :required
           :rationale "無期限の開示は同意ではなく譲渡"})

    (and (string? issued-on) (string? expires-on)
         (not (pos? (- (iso->epoch-day expires-on) (iso->epoch-day issued-on)))))
    (conj {:field :expires-on :problem :must-be-after-issued-on})

    (and (string? issued-on) (string? expires-on)
         (> (- (iso->epoch-day expires-on) (iso->epoch-day issued-on)) max-lifetime-days)
         )
    (conj {:field :expires-on
           :problem :exceeds-max-lifetime
           :max-days max-lifetime-days})))

(defn grant
  "開示 grant を構築する。成立しなければ throw。"
  [g]
  (let [ps (problems g)]
    (when (seq ps)
      (throw (ex-info "この開示 grant は成立しない" {:problems ps})))
    g))

(defn resource-uri
  "CACAO の `resources` に載せる 1 行。`cacao/mint` の :resources へ渡す。

  欄名を URI に並べるので、検証側は CACAO を検証するだけで「この相手はこの欄まで」
  を知れる —— 別途 ACL を引かない。"
  [{:keys [rid fields purpose]}]
  (str "rirekisho://disclose/" rid
       "?fields=" (->> fields (map name) sort (interpose ",") (apply str))
       "&purpose=" purpose))

(defn project
  "grant の欄だけに絞った履歴書を返す(選択的開示)。

  `select-keys` を直に呼ばないのは、grant に無い欄が 1 つでも混ざる経路を
  1 箇所に閉じ込めたいため。ここを通らない開示を作らない。"
  [rirekisho {:keys [fields]}]
  (select-keys rirekisho fields))

(defn revoke
  "grant を失効させる tx-data を返す。**フラグを立てず retract する。**

  戻り値の `:already-disclosed` は「この失効では取り消せないもの」——
  相手が期間中に復号した平文は、この操作では消えない。"
  [{:keys [db-id fields recipient] :as _grant} {:keys [disclosed?] :or {disclosed? true}}]
  {:tx-data [[:db/retractEntity db-id]]
   :already-disclosed (when disclosed?
                        {:recipient recipient
                         :fields fields
                         :note "失効は将来の開示だけを止める。期間中に復号された平文は取り消せない"})})
