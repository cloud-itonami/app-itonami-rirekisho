# cloud-itonami-rirekisho

**履歴書・職務経歴書の値モデルと、「運営者に読ませない」ための不変条件。**
yagish のような履歴書作成サービスを、*運営者が本人データを読めない*構造で建てるための
中核ライブラリ。依存ゼロの `.cljc` で、暗号 provider・store・identity は注入する。

## 様式は JIS ではない（もう存在しない）

履歴書の様式例は **JIS Z 8303 から 2020-07 の改正で削除された**。きっかけは LGBT
当事者団体による性別欄削除の要請で、2021-04-16 に厚生労働省が差し替えとなる
[新たな履歴書の様式例](https://www.mhlw.go.jp/) を公表した。この repo は厚労省様式例
だけを正本とする。「JIS 規格の履歴書テンプレート」を名乗る実装は既に古い。

2021 様式例の変更点を、この repo は**表示ではなく型で**実装する:

| 変更 | ここでの実装 |
|---|---|
| 性別欄を `[男・女]` の選択から**自由記述の任意欄**へ | `:gender-note` は文字列で `nil` 可。列挙型にしない |
| **通勤時間 / 扶養家族数 / 配偶者 / 配偶者の扶養義務** の 4 欄を設けない | `model/removed-fields`。書き込むと **refuse** する |

4 欄を「既定で空欄」にするだけの実装は、テンプレートを 1 つ足せば復活する。
構造として持てないようにするのが様式例の趣旨に合う。

## 2 つの面 — クエリできる面と、読めない面

**暗号文は join できない。** だから「サーバに読ませない」と「Datalog でクエリする」は
同じ datom には同時に成立しない。片方を諦めるのではなく、**何をクエリしたいかを先に
決めて、それだけを非識別な射影として平文で書く**。

| 面 | 中身 | 運営者に見えるか | ns |
|---|---|---|---|
| ① 封筒 | 履歴書本体（氏名・生年月日・住所・写真・学歴の記述） | **見えない** | `rirekisho.envelope` |
| ② cohort | ISCO-08 / 国 / seniority / skill 集合 / count | 見える（**PII ゼロ**） | `rirekisho.cohort` |

②が Datalog の到達範囲。`kotobase` の join はちょうど ref 1 本しか届かないので、
**①と②を跨ぐ分析は N クエリ + マージになる** — 黙ってシャーディングしない。

この分割は新案ではない。`kotoba-lang/kagi`（index/ACL は Datomic graph、secret は
SealedBlockStore）と `etzhayyim/com-etzhayyim-talent`（cohort は graph、PII は
ciphertext）が既に採っている形で、これが 3 つ目の実装。

### talent との違い — プレフィックス検査ではなく封緘そのもの

talent は「識別項目は `signal:v1:` 暗号文のみ」という同じ規則を持つが、その実装は
**文字列プレフィックスの検査**で、暗号化自体は呼び出し側任せになっている
（`talent/methods/agent.cljc`）。`"signal:v1:" + 平文` を書けば通る。

ここでは封筒を作れる関数を `seal` 1 本に絞り、`persistable?` は「プレフィックスが
付いているか」ではなく「**この値が seal の出力そのものか**」を見る。未知の鍵が
あっても通さない（通すと schema が増えるたびに関門が緩む）。

## 同意は CACAO — VC ではない

この workspace に W3C Verifiable Credentials の実装は無い（`org-w3-did` はあるが
VC data model / SD-JWT / BBS+ の選択的開示は無い）。一方
[`org-chainagnostic-cacao`](https://github.com/kotoba-lang/org-chainagnostic-cacao)
は実装済みで、**保有者自身のランタイムで mint** でき、`resources` に権限を書け、
期限を持つ。VC でやりたかったこと（自己主権的・検証者がサーバに問い合わせず検証できる・
必要な項目だけ出す）は CACAO の resources で満たせる。

```clojure
(disclosure/resource-uri
 {:rid "rid:zRESUME" :fields #{:name :history} :purpose "2026年度 中途採用選考"})
;; => "rirekisho://disclose/rid:zRESUME?fields=history,name&purpose=2026年度 中途採用選考"
```

欄名を URI に並べるので、**検証側は CACAO を検証するだけで「この相手はこの欄まで」を
知れる** — 別途 ACL を引かない。

### 暗号は失効を与えない

一度渡した平文は取り返せない。CACAO の期限が切れても、相手の手元にある復号済みの
履歴書は消えない。`revoke` は将来の開示だけを止め、それを戻り値の
`:already-disclosed` に**明示する** — 「失効しました」と表示して既に渡ったものが
消えたと読ませない。失効自体は talent（ADR-0018）と同じく **hard retract**で、
フラグは立てない（フラグを読み落とした 1 つのクエリがそのまま漏洩になる）。

## 暗号 provider は注入する

この repo は kagi に依存しない。`seal-fn` / `open-fn` を受け取るだけ:

- ブラウザ: `kagi.crypto.noble/noble-provider` — 同期・純 JS `@noble/*`・Rust なし。
  X25519+ML-KEM-768 / Ed25519+ML-DSA-65 の hybrid をブラウザでも縮退させない。
- JVM: `kagi.crypto/jvm-provider` — JDK 24 標準 ML-KEM-768 / ML-DSA-65。

両者の相互運用（JVM が封緘した封筒をブラウザが開ける／その逆）は kagi 側の双方向
テストが実ベクタで保証している。ここで責務を二重に持たない。

## 開発

```sh
clojure -M:test    # 28 tests / 62 assertions
clojure -M:lint
```

## まだ無いもの

正直に書く。この repo は**中核の値モデルと不変条件**であって、製品ではない。

- **UI が無い。** 入力フォームも印刷レイアウトも未実装。書くときは skill
  `kotoba-uiux`（`kotoba-ui.core` 単一エントリ、raw hex 禁止、layout は shell から）に従う。
- **職務経歴書（`rirekisho.shokureki`）が未実装。** 編年式／逆編年式／キャリア式の
  3 形式を想定しているが、まだ型が無い。
- **nekko RID との配線が未実装。** 「履歴書 1 通 = 1 RID」（signed ref で版を検証、
  ホストが消えても identity が残る）は設計として決まっているが、コードはまだ `:rid`
  を文字列として受け取るだけ。
- **x402 課金が未配線。** `nexus-x402`（`x402.nexus`、稼働中）に seller 登録して
  prepaid quota を CACAO の `resources` に焼く設計だが、未実装。
- **kagi の SealedBlockStore の B2/IPFS/Storj adapter が未実装**（kagi 側の既知の穴）。
  ①の面は現状ローカルにしか着地しない。

## License

AGPL-3.0-or-later
