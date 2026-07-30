(ns rirekisho.envelope-test
  "provider は注入なので、ここでは kagi を持ち込まず可逆な fake で不変条件だけを見る。
  実暗号との相互運用は kagi 側の双方向テストが受け持つ(責務を二重に持たない)。"
  (:require [clojure.test :refer [deftest is testing]]
            [rirekisho.envelope :as env]))

(def ^:private resume
  {:name "川崎 純" :name-kana "かわさき じゅん"
   :date-of-birth "1985-01-01"
   :history [{:year 2016 :month 4 :description "◯◯大学 入学"}]})

;; 可逆な代用。seal/open の *配線*だけを見るためのもので、暗号強度は見ない。
(defn- fake-seal [plaintext aad]
  {:dek "DEK" :nonce "NONCE" :ciphertext (str "enc(" aad "):" plaintext)})

(defn- fake-open [_dek _nonce ciphertext aad]
  (subs ciphertext (count (str "enc(" aad "):"))))

(deftest sealing-round-trips
  (let [{:keys [envelope dek]} (env/seal fake-seal "rid:zRESUME" resume)]
    (is (= "DEK" dek))
    (is (= resume (env/open fake-open dek envelope)))))

(deftest the-dek-is-not-inside-the-envelope
  (testing "封筒と鍵を同じ map で返すと、丸ごと永続化した時に封緘の意味が消える"
    (let [{:keys [envelope]} (env/seal fake-seal "rid:zRESUME" resume)]
      (is (not-any? #{:dek} (keys envelope)))
      (is (env/persistable? envelope)))))

(deftest a-plaintext-resume-is-not-persistable
  (is (false? (env/persistable? resume)))
  (is (= #{:name :name-kana :date-of-birth :history} (env/leaks resume)))
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (env/ensure-persistable! resume))))

(deftest an-envelope-with-one-plaintext-field-added-is-not-persistable
  (testing "封筒に見えても、平文が 1 つ混ざれば通さない"
    (let [{:keys [envelope]} (env/seal fake-seal "rid:zRESUME" resume)
          tainted (assoc envelope :name "川崎 純")]
      (is (false? (env/persistable? tainted)))
      (is (= #{:name} (env/leaks tainted))))))

(deftest an-unknown-key-is-refused-rather-than-assumed-harmless
  (testing "知らない鍵を通すと、schema が増えるたびにこの関門が緩む"
    (let [{:keys [envelope]} (env/seal fake-seal "rid:zRESUME" resume)]
      (is (false? (env/persistable? (assoc envelope :envelope/note "後で消す")))))))

(deftest a-prefix-alone-does-not-make-a-value-persistable
  (testing "talent の signal:v1: 検査が通してしまう形 —— ここでは通らない"
    (is (false? (env/persistable? {:name "signal:v1:川崎 純"})))))
