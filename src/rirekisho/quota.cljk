(ns rirekisho.quota
  "前払い quota を CACAO の `resources` に焼く。

  ## なぜ従量課金にしないのか

  x402 の on-chain verify は Base の JSON-RPC で、1 回の検証が 1 トランザクションに
  対応する。封筒 1 通の保管が数 KB・数円未満であることを考えると、**byte 単位の課金は
  検証の粒度より細かい** —— 支払いより検証のコストが高くつく。

  そこで一度まとめて払い、買った量を CACAO の `resources` に載せる。CACAO は
  `rirekisho.disclosure` が既に開示権限を運んでいる器なので、**支払い証明と権限が
  同じトークンに乗り**、面が増えない。

  ## quota は台帳ではない。**これは主張であって残高ではない**

  CACAO に「10 MB 買った」と書いてあっても、それが何回使われたかは書いていない。
  純粋な関数がどれだけ検査しても二重使用は防げない —— 防げるのは**どこかに永続化
  された使用量カウンタ**だけ。

  だから `remaining` は `consumed` を**引数で要求する**。省略可能にすると、カウンタを
  持たないまま『quota を確認した』と言える経路ができてしまう。数字がどこから来るかは
  呼び出し側の責任で、この ns はその数字なしには答えを出さない。"
  (:require [kotoba.lang.text :as str]))

(def scheme "x402://quota/")

(def ^:const max-lifetime-days
  "1 つの quota が持てる最長期間。無期限の前払いは、事業をやめた後も債務が残る。"
  365)

(defn resource-uri
  "CACAO の `resources` に載せる 1 行。

  `:payment` は支払いの参照（Base の tx hash）。**これが無い quota は主張ですらない**
  ので必須にする —— 後から『払った』と言われた時に照合するものが要る。"
  [{:keys [order-id bytes months discloses payment expires-on]}]
  (when-not (and (string? order-id) (string? payment) (string? expires-on))
    (throw (ex-info "a quota needs an :order-id, a :payment reference and an :expires-on"
                    {:order-id order-id :payment payment :expires-on expires-on})))
  (str scheme order-id
       "?bytes=" (or bytes 0)
       "&months=" (or months 0)
       "&discloses=" (or discloses 0)
       "&expires=" expires-on
       "&payment=" payment))

(defn parse
  "resource URI → map。この ns の resource でなければ nil（他人の resource を
  自分のものとして読まない）。"
  [uri]
  (when (and (string? uri) (str/starts-with? uri scheme))
    (let [[order-id q] (str/split (subs uri (count scheme)) #"\?" 2)
          params (into {}
                       (map (fn [pair]
                              (let [[k v] (str/split pair #"=" 2)]
                                [(keyword k) v])))
                       (str/split (or q "") #"&"))]
      {:order-id order-id
       :bytes (parse-long (or (:bytes params) "0"))
       :months (parse-long (or (:months params) "0"))
       :discloses (parse-long (or (:discloses params) "0"))
       :expires-on (:expires params)
       :payment (:payment params)})))

(defn- iso->epoch-day [s]
  #?(:clj (try (.toEpochDay (java.time.LocalDate/parse s)) (catch Exception _ nil))
     :cljs (let [t (.parse js/Date s)]
             (when-not (js/isNaN t) (js/Math.floor (/ t 86400000))))))

(defn problems
  "quota として成立しない点を列挙する。"
  [{:keys [order-id bytes months discloses payment expires-on]} issued-on]
  (vec
   (concat
    (when (str/blank? (or order-id "")) [{:field :order-id :problem :required}])
    (when (str/blank? (or payment ""))
      [{:field :payment :problem :required
        :rationale "支払いの参照が無い quota は、後で照合するものが無い"}])
    (when-not (iso->epoch-day expires-on)
      [{:field :expires-on :problem :required-and-must-be-a-date}])
    (when (and (iso->epoch-day expires-on) (iso->epoch-day issued-on)
               (> (- (iso->epoch-day expires-on) (iso->epoch-day issued-on))
                  max-lifetime-days))
      [{:field :expires-on :problem :exceeds-max-lifetime :max-days max-lifetime-days}])
    (when (every? #(or (nil? %) (zero? %)) [bytes months discloses])
      [{:field :amount :problem :buys-nothing}])
    (when (some #(and (some? %) (neg? %)) [bytes months discloses])
      [{:field :amount :problem :must-not-be-negative}]))))

(defn expired? [{:keys [expires-on]} today]
  (let [e (iso->epoch-day expires-on) t (iso->epoch-day today)]
    (boolean (and e t (< e t)))))

(defn remaining
  "残量。**`consumed` は必須** —— 使用量カウンタを持たないまま『quota を確認した』と
  言える経路を作らないため。`consumed` は `{:bytes :discloses}`。

  返すのは残量そのものではなく `{:remaining {..} :expired? bool}`。期限切れを
  残量ゼロと同じ形で返すと、呼び出し側が『使い切った』と『期限切れ』を取り違える。"
  [{:keys [bytes discloses] :as quota} consumed today]
  (when-not (map? consumed)
    (throw (ex-info "remaining needs a :consumed map from a durable counter"
                    {:note "quota は主張であって残高ではない。二重使用を防ぐのはカウンタだけ"})))
  {:expired? (expired? quota today)
   :remaining {:bytes (max 0 (- (or bytes 0) (or (:bytes consumed) 0)))
               :discloses (max 0 (- (or discloses 0) (or (:discloses consumed) 0)))}})

(defn covers?
  "この使用を quota が賄えるか。期限切れなら残量に関係なく false。"
  [quota consumed today {:keys [bytes discloses] :or {bytes 0 discloses 0}}]
  (let [{:keys [expired? remaining]} (remaining quota consumed today)]
    (and (not expired?)
         (<= bytes (:bytes remaining))
         (<= discloses (:discloses remaining)))))
