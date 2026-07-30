(ns rirekisho.provenance-nekko-test
  "`rirekisho.provenance` が **本物の `nekko.sigref` と相互運用できる**ことの証明。

  「同じ形にしてある」と docstring に書くだけでは、片方が変わった時に誰も気づかない。
  ここでは実 `cbor.core`（DAG-CBOR）と実 `ed25519.core` を注入して attestation を作り、
  それを **`nekko.sigref/valid?` に通す** —— 逆向きに、nekko が署名したものを
  `provenance/valid?` が受け入れることも見る。

  nekko と ed25519 は **test-only の依存**（`:nekko` alias）。この repo の実行時は
  依存ゼロのままで、ブラウザ側は `kagi.crypto.noble` の Ed25519 を注入して同じことをする。"
  (:require [cbor.core :as cbor]
            [clojure.test :refer [deftest is testing]]
            [ed25519.core :as ed]
            [nekko.sigref :as sigref]
            [rirekisho.provenance :as prov]))

(def ^:private seed (byte-array (range 32)))
(def ^:private did (ed/did-key-from-seed seed))

(def ^:private ctx
  {:encode-fn cbor/encode
   :sign-fn (fn [bytes] (ed/hexify (ed/sign seed bytes)))
   :verify-fn (fn [signer bytes sig] (ed/verify-did signer bytes (ed/unhex sig)))
   :digest-fn (fn [^String s] (str "sha256:" (hash s)))
   :did did})

(def ^:private rid "bafyreiexamplerid")
(def ^:private commit "bafyreiexamplecommit")
(def ^:private ts "2026-07-30T00:00:00Z")

(deftest nekko-accepts-what-rirekisho-signs
  (testing "受け手が nekko しか持っていなくても、この attestation を検証できる"
    (let [a (prov/sign ctx (prov/attestation {:rid rid :version 1
                                              :commit commit :ts ts}))]
      (is (true? (sigref/valid? a))))))

(deftest rirekisho-accepts-what-nekko-signs
  (testing "逆向き —— nekko が切った sigref をこちらが受け入れる"
    (let [s (sigref/sign seed rid "refs/rirekisho/v1" commit ts)]
      (is (true? (prov/valid? ctx s))))))

(deftest the-two-produce-the-same-bytes
  (testing "同じ (rid, ref, commit, ts) なら署名まで一致する（Ed25519 は決定論的）"
    (let [mine (prov/sign ctx (prov/attestation {:rid rid :version 1
                                                 :commit commit :ts ts}))
          theirs (sigref/sign seed rid "refs/rirekisho/v1" commit ts)]
      (is (= (get theirs "sig") (get mine "sig")))
      (is (= (get theirs "signer") (get mine "signer")))
      (is (= (select-keys theirs ["rid" "ref" "commit" "ts"])
             (select-keys mine ["rid" "ref" "commit" "ts"]))))))

(deftest nekko-rejects-a-tampered-rirekisho-attestation
  (let [a (prov/sign ctx (prov/attestation {:rid rid :version 1
                                            :commit commit :ts ts}))]
    (is (false? (sigref/valid? (assoc a "commit" "bafyreisomethingelse"))))))

(deftest a-version-bump-changes-the-ref-and-the-signature
  (testing "ある版の署名を別の版のものとして使い回せない"
    (let [v1 (prov/sign ctx (prov/attestation {:rid rid :version 1
                                               :commit commit :ts ts}))
          v2 (prov/sign ctx (prov/attestation {:rid rid :version 2
                                               :commit commit :ts ts}))]
      (is (not= (get v1 "sig") (get v2 "sig")))
      (is (false? (sigref/valid? (assoc v1 "ref" (get v2 "ref"))))))))
