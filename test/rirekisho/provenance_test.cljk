(ns rirekisho.provenance-test
  "注入した偽の暗号で、この ns 自身の判断（何を照合し、何を拒むか）を見る。
  nekko との**バイト互換**は `provenance-nekko-test` が実 CBOR・実 Ed25519 で見る。"
  (:require [kotoba.lang.text :as str]
            [clojure.test :refer [deftest is testing]]
            [rirekisho.provenance :as prov]))

(def ^:private envelope
  {:envelope/rid "rid:zRESUME" :envelope/ciphertext "CIPHERTEXT" :envelope/nonce "N"})

(defn- fake-encode [m] (pr-str (into (sorted-map) m)))
(defn- fake-digest [s] (str "digest(" s ")"))
(defn- fake-sign [bytes] (str "sig:" bytes))
(defn- fake-verify [_did bytes sig] (= sig (str "sig:" bytes)))

(def ^:private ctx
  {:encode-fn fake-encode :sign-fn fake-sign :verify-fn fake-verify
   :digest-fn fake-digest :did "did:key:zHOLDER"})

(defn- signed [& {:keys [version commit]
                  :or {version 1 commit "digest(CIPHERTEXT)"}}]
  (prov/sign ctx (prov/attestation {:rid "rid:zRESUME" :version version
                                    :commit commit :ts "2026-07-30T00:00:00Z"})))

(deftest a-ref-names-its-version
  (is (= "refs/rirekisho/v3" (prov/ref-name 3)))
  (is (= 3 (prov/version-of "refs/rirekisho/v3")))
  (testing "他人の ref を自分のものと読まない"
    (is (nil? (prov/version-of "refs/heads/main")))))

(deftest a-version-must-be-an-integer
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (prov/ref-name "1"))))

(deftest the-content-address-measures-the-ciphertext
  (testing "平文を測るとダイジェストが平文の存在証明になってしまう"
    (is (= "digest(CIPHERTEXT)" (prov/content-address fake-digest envelope)))))

(deftest an-envelope-without-ciphertext-has-no-address
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (prov/content-address fake-digest {:envelope/rid "r"}))))

(deftest a-signed-attestation-verifies
  (is (true? (prov/valid? ctx (signed)))))

(deftest tampering-with-any-signed-field-breaks-the-signature
  (doseq [k ["rid" "ref" "commit" "ts"]]
    (testing (str k " を書き換えると verify に落ちる")
      (is (false? (prov/valid? ctx (assoc (signed) k "tampered")))))))

;; ───────── 受け取った側の検査 ─────────

(deftest a-matching-delivery-is-acceptable
  (is (prov/acceptable? ctx (signed) envelope {:rid "rid:zRESUME" :version 1}))
  (is (empty? (prov/problems ctx (signed) envelope {}))))

(deftest a-swapped-envelope-is-caught
  (testing "受け取った暗号文が、署名された時のものと違う"
    (let [p (first (prov/problems ctx (signed)
                                  (assoc envelope :envelope/ciphertext "OTHER") {}))]
      (is (= :digest-mismatch (:problem p)))
      (is (= "digest(OTHER)" (:actual p))))))

(deftest an-old-version-presented-as-the-current-one-is-caught
  (let [p (first (prov/problems ctx (signed :version 1) envelope {:version 2}))]
    (is (= :not-the-expected-version (:problem p)))
    (is (= "refs/rirekisho/v2" (:expected p)))))

(deftest someone-elses-resume-is-caught
  (let [p (first (prov/problems ctx (signed) envelope {:rid "rid:zOTHER"}))]
    (is (= :not-the-expected-resume (:problem p)))))

(deftest every-failing-check-is-reported-not-just-the-first
  (testing "受け手は一度で全部の食い違いを見たい"
    (let [ps (prov/problems ctx (assoc (signed) "sig" "forged")
                            (assoc envelope :envelope/ciphertext "OTHER")
                            {:rid "rid:zOTHER" :version 9})]
      (is (= #{:signature :content :rid :version} (into #{} (map :check) ps))))))

(deftest the-attestation-carries-exactly-nekkos-four-keys
  (testing "nekko.sigref の payload と同じ形でないと、JVM 側の検証者が使えない"
    (is (= #{"rid" "ref" "commit" "ts"}
           (set (keys (prov/attestation {:rid "r" :version 1 :commit "c"
                                         :ts "2026-07-30T00:00:00Z"})))))
    (is (= #{"rid" "ref" "commit" "ts" "signer" "sig"} (set (keys (signed)))))))

(deftest the-signature-covers-only-the-four-payload-keys
  (testing "signer/sig を含めて署名すると自己参照になり、検証側で組み立て直せない"
    (let [a (signed)]
      (is (str/includes? (get a "sig") "\"commit\""))
      (is (not (str/includes? (get a "sig") "\"signer\""))))))
