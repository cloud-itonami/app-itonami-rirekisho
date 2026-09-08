(ns rirekisho.provenance
  "履歴書 1 通 = 1 RID。**受け取った側が、受け取ったものを検証できる**ようにする。

  ## 何を証明するのか

  企業が受け取るのは封筒（暗号文）と、それを開ける DEK と、この attestation。
  三つ揃えば受け手は次を自分で確かめられる:

  1. **改竄されていない** —— 受け取った暗号文のダイジェストが、attestation の
     `commit` と一致する。
  2. **本人が出した** —— 署名が本人の did:key で verify できる。
  3. **どの版か** —— `ref` が `refs/rirekisho/v<N>`。別の版を今の版だと言い張れない。

  我々（運営者）は 1 つも関与しない。ホストが消えても、企業の手元にある 3 点だけで
  検証は成立する —— これが `nekko`（Radicle 相当）を使う理由で、『信用してください』
  と書く代わりに検証させる。

  ## nekko とバイト互換だが、依存はしない

  attestation の平文部分は `nekko.sigref` と**同じ形**（`{\"rid\" \"ref\" \"commit\" \"ts\"}`）
  なので、JVM 側の検証者は `nekko.sigref/valid?` をそのまま使える。それでいてこの ns は
  依存ゼロを保つ —— CBOR エンコーダも署名器もダイジェストも**注入**する。

  なぜそこまでするか: `nekko.sigref` は `ed25519.core` に直接依存しており、その ns は
  今日 JVM 専用（nekko 自身の docstring がそう書いている）。依存すると、この repo の
  ブラウザ側が丸ごと動かなくなる。注入なら、ブラウザは `kagi.crypto.noble`（純 JS の
  Ed25519）を、JVM は `ed25519.core` を渡せる。

  バイト互換は主張ではなく**テストで確かめている** —— `provenance_nekko_test` が実
  `cbor.core` と実 `ed25519.core` を注入して作った attestation を、実
  `nekko.sigref/valid?` に通す。"
  (:require [kotoba.lang.text :as str]))

(def ref-prefix "refs/rirekisho/")

(defn ref-name
  "この版を指す ref 名。版ごとに別の ref なので、ある版の署名を別の版のものとして
  使い回せない。"
  [version]
  (when-not (integer? version)
    (throw (ex-info "a version is an integer" {:version version})))
  (str ref-prefix "v" version))

(defn version-of
  "ref 名 → 版。この ns の ref でなければ nil。"
  [ref]
  (when (and (string? ref) (str/starts-with? ref ref-prefix))
    (parse-long (subs ref (inc (count ref-prefix))))))

(defn content-address
  "封筒の暗号文のダイジェスト。`digest-fn` は `[bytes-or-string] -> string`。

  **平文ではなく暗号文を測る。** 平文を測ると、そのダイジェストが平文の存在証明に
  なってしまい（辞書攻撃で当てられる短い履歴書はいくらでもある）、封緘の意味が減る。"
  [digest-fn {:keys [:envelope/ciphertext]}]
  (when (nil? ciphertext)
    (throw (ex-info "an envelope with no ciphertext has no content address" {})))
  (digest-fn ciphertext))

(defn attestation
  "署名前の平文部分。`nekko.sigref` の payload と同じ 4 キー・同じ名前。"
  [{:keys [rid version commit ts]}]
  (when-not (and (string? rid) (string? commit) (string? ts))
    (throw (ex-info "attestation needs string :rid, :commit and :ts"
                    {:rid rid :commit commit :ts ts})))
  {"rid" rid "ref" (ref-name version) "commit" commit "ts" ts})

(defn- payload-bytes [encode-fn a]
  (encode-fn {"rid" (get a "rid") "ref" (get a "ref")
              "commit" (get a "commit") "ts" (get a "ts")}))

(defn sign
  "attestation に署名を載せる。→ `nekko.sigref/sign` と同じ形の map。

  `encode-fn` は canonical bytes（nekko は DAG-CBOR）、`sign-fn` は
  `[bytes] -> signature-string`、`did` は署名者の did:key。"
  [{:keys [encode-fn sign-fn did]} a]
  (when-not (and (fn? encode-fn) (fn? sign-fn) (string? did))
    (throw (ex-info "sign needs :encode-fn, :sign-fn and a did:key :did" {})))
  (assoc a "signer" did "sig" (sign-fn (payload-bytes encode-fn a))))

(defn valid?
  "署名がその attestation 自身の signer で verify できるか。
  `verify-fn` は `[did bytes sig] -> boolean`。"
  [{:keys [encode-fn verify-fn]} sigref]
  (boolean
   (when-let [sig (get sigref "sig")]
     (verify-fn (get sigref "signer") (payload-bytes encode-fn sigref) sig))))

(defn problems
  "**受け取った側の検査。** 期待と食い違う点を全部返す。空なら受け取ってよい。

  `expected` は `{:rid :version}`（応募者から別経路で伝えられた、あるいは
  以前の版から分かっているもの）。省略した項目は照合しない。"
  ;; encode-fn/verify-fn は `valid?` が ctx から読む。ここで分解すると使っていない
  ;; ように見えるので、ctx のまま渡す。
  [{:keys [digest-fn] :as ctx} sigref envelope {:keys [rid version] :as _expected}]
  (vec
   (concat
    (when-not (valid? ctx sigref)
      [{:check :signature :problem :does-not-verify
        :rationale "署名者本人が出したものだと確かめられない"}])

    (let [actual (content-address digest-fn envelope)]
      (when-not (= actual (get sigref "commit"))
        [{:check :content :problem :digest-mismatch
          :attested (get sigref "commit") :actual actual
          :rationale "受け取った暗号文は、署名された時のものと違う"}]))

    (when (and rid (not= rid (get sigref "rid")))
      [{:check :rid :problem :not-the-expected-resume
        :expected rid :actual (get sigref "rid")}])

    (when (and version (not= (ref-name version) (get sigref "ref")))
      [{:check :version :problem :not-the-expected-version
        :expected (ref-name version) :actual (get sigref "ref")}]))))

(defn acceptable?
  [ctx sigref envelope expected]
  (empty? (problems ctx sigref envelope expected)))
