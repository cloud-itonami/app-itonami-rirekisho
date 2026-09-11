(ns rirekisho.store
  "封筒をどこに置くか。

  `rirekisho.envelope` は封筒を**作る**が、置き場は持たない。ここがその口で、
  `kagi.store/object-sealed-block-store` と**同じ 4 関数**
  （`{:get-object :put-object :exists?}`、`storj.store/store-fns` が返す形）を受け取る。

  ## なぜ kagi に依存しないのか

  依存すると、この repo を fork した人が Storj も B2 も使わないのに kagi の依存木を
  引くことになる。4 関数を受け取るだけなら、両方に依存するのは配線するアプリだけで済む
  —— `storj.store` の ns docstring が明示している設計意図で、kagi 自身も同じ理由で
  io-storj に依存していない。

  ## 鍵は保存しない

  `put!` は DEK を**返す**が、置き場には渡さない。封筒と DEK を同じ場所に置いたら
  封緘の意味が消える。DEK をどこに保つか（kagi の compartment、開示相手への
  grant envelope、ユーザーの手元）はこの ns の管轄外で、**受け取った側が決める**。
  ここで既定の保存先を用意すると、その既定が『安全な置き場』だと読まれてしまう。"
  (:require [rirekisho.envelope :as envelope]))

(defn- object-key
  "封筒 1 通が占める object の名前。版を含むので、1 キー = 1 版になる
  （kagi 側の上書き拒否がこの前提の上に立っている）。"
  [rid version]
  (str "rirekisho/" rid "/v" version))

(defn put!
  "履歴書を封緘して置く。→ `{:key :envelope :dek :version}`。

  `seal-fn` は `[plaintext aad] -> {:dek :nonce :ciphertext}`
  （= provider を部分適用した `kagi.crypto/seal-item`）。

  **平文が置き場に渡らないことは、置く直前に検査する** ——
  `envelope/ensure-persistable!` を通さない経路をここに作らない。"
  [{:keys [put-object]} seal-fn {:keys [rid version]} rirekisho]
  (when-not (fn? put-object)
    (throw (ex-info "rirekisho.store/put! needs :put-object" {})))
  (when-not (and (string? rid) (integer? version))
    (throw (ex-info "put! needs a string :rid and an integer :version"
                    {:rid rid :version version})))
  (let [{:keys [envelope dek]} (envelope/seal seal-fn rid rirekisho)
        k (object-key rid version)]
    (envelope/ensure-persistable! envelope)
    (put-object k (:envelope/ciphertext envelope))
    {:key k :envelope envelope :dek dek :version version}))

(defn get!
  "置いた封筒を読み戻して開ける。→ 履歴書、無ければ nil。

  `open-fn` は `[dek nonce ciphertext aad] -> plaintext-string`。nonce は封筒側に
  あるので、呼び出し側は `put!` が返した `:envelope` を保持しておく必要がある
  ——暗号文だけを object store に置き、nonce/alg は封筒の metadata として
  別に持つ設計なので、metadata を捨てると開けなくなる。"
  [{:keys [get-object]} open-fn dek {:keys [:envelope/rid] :as envelope} version]
  (when-not (fn? get-object)
    (throw (ex-info "rirekisho.store/get! needs :get-object" {})))
  (when-let [ciphertext (get-object (object-key rid version))]
    (envelope/open open-fn dek (assoc envelope :envelope/ciphertext ciphertext))))

(defn stored?
  "この版が置き場にあるか。`exists?` という名前にしないのは `cljs.core/exists?` を
  隠すため —— core の同名 var を上書きすると、この ns を読む人が別物を呼ぶ。"
  [fns rid version]
  (let [f (:exists? fns)]
    (boolean (when f (f (object-key rid version))))))
