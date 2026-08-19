# mini-oms — マルチモール受注統合ミニOMS

複数のECモールから受注を取り込み、ステータス管理・在庫引当・出荷確定までを行う受注管理システム(OMS)です。
モールごとに異なるAPI形式(JSON / XML)を吸収し、受注のライフサイクルと在庫を破綻なく回すことを目的にしています。

**このリポジトリは「動くこと」より「なぜそう作ったか」を読めることを重視しています。**

---

## このリポジトリの読み方

設計意図を4つの場所に分けて書いています。

| 場所 | 表現するもの |
|---|---|
| コード | **How** — 実装の詳細 |
| テスト | **What** — 仕様。テスト名とアサーションを読めば業務ルールが分かる |
| コミットメッセージ | **Why** — なぜこの変更をしたか。業務背景・設計判断 |
| コメント | **Why not** — なぜ他の選択肢を採らなかったか |

テストメソッド名は日本語です。`出荷指示済みの受注はキャンセルできない()` のように、
テスト一覧がそのまま業務仕様書として読めることを優先しています。

`git log` を追うと、各機能をなぜその形にしたかが順に読めます。

---

## 動かす

必要なもの: Docker / JDK 21

```bash
docker compose up -d      # PostgreSQL 16 を起動
./gradlew bootRun         # アプリ起動(Flyway がスキーマと初期データを作成)
```

起動すると 1分ごとにモックモールAPIから受注を取り込みます。

```
受注取込完了: 取込3件 スキップ0件 失敗0件 取得失敗モール[]
受注取込完了: 取込0件 スキップ3件 失敗0件 取得失敗モール[]   ← 2回目は冪等キーで全件スキップ
```

### 一連の業務フローを試す

```bash
# 受注一覧(未確認のものを古い順に)
curl "http://localhost:8080/api/orders?status=NEW"

# 在庫の初期状態
curl "http://localhost:8080/api/stocks"

# 確認 → 在庫が引き当てられる(実在庫は減らない)
curl -X POST "http://localhost:8080/api/orders/1/confirmation" \
     -H "Content-Type: application/json" -d '{"version":0}'

# 出荷指示 → 在庫は動かない
curl -X POST "http://localhost:8080/api/orders/1/shipping-instruction" \
     -H "Content-Type: application/json" -d '{"version":1}'

# 出荷完了 → 引当が実在庫から落ちる
curl -X POST "http://localhost:8080/api/orders/1/shipment" \
     -H "Content-Type: application/json" -d '{"version":2}'
```

在庫の動きは `GET /api/stocks` で追えます。

```
確認    実10 引2 可8     ← 引当。実在庫は減らない
出荷指示 実10 引2 可8     ← 在庫は動かない
出荷完了 実 8 引0 可8     ← 引当が実在庫から落ちる
キャンセル(確認済から)     ← 引当が戻る
```

> PowerShell では `curl` が `Invoke-WebRequest` のエイリアスのため、`curl.exe` を使い
> ボディは `-d "{\"version\":0}"` のようにエスケープしてください。

---

## 何ができるか

### 受注の取込

2つのモール(モックAPI)から定期的に受注を取り込みます。形式は意図的に揃えていません。

| | モールA | モールB |
|---|---|---|
| 形式 | JSON | XML |
| 日時 | ISO-8601 (`2026-08-19T10:00:00+09:00`) | 独自形式・TZなし (`20260819103000`) |
| ステータス | なし | 独自コード (`01`/`03`/`09`...) |

この差異はモールごとのクライアント(腐敗防止層)が吸収し、上位層にはドメインの受注だけが渡ります。

### 受注のライフサイクル

```mermaid
stateDiagram-v2
    [*] --> NEW: モールから取込
    NEW --> CONFIRMED: 確認 ── 在庫を引き当てる
    NEW --> CANCELLED: キャンセル
    CONFIRMED --> SHIPPING_INSTRUCTED: 出荷指示
    CONFIRMED --> CANCELLED: キャンセル ── 引当を解除する
    SHIPPING_INSTRUCTED --> SHIPPED: 出荷完了 ── 実在庫から落とす
    SHIPPED --> RETURNED: 返品
    CANCELLED --> [*]
    RETURNED --> [*]
```

出荷指示後はキャンセルできません。倉庫で既にピッキングが走っている可能性があり、
システム上だけキャンセルすると実在庫と引当の不整合を生むためです
(実務では「出荷指示取消」という別フローで対応します。本デモではスコープ外)。

### API

| メソッド | パス | 用途 |
|---|---|---|
| GET | `/api/orders?status=&mallCode=&page=&size=` | 受注一覧(注文日時の古い順) |
| GET | `/api/orders/{id}` | 受注詳細(明細つき) |
| POST | `/api/orders/{id}/confirmation` | 確認(在庫引当) |
| POST | `/api/orders/{id}/shipping-instruction` | 出荷指示 |
| POST | `/api/orders/{id}/shipment` | 出荷完了(実在庫から落とす) |
| POST | `/api/orders/{id}/cancellation` | キャンセル(引当解除) |
| POST | `/api/orders/{id}/return` | 返品 |
| GET | `/api/stocks` | 在庫一覧(実在庫・引当済・引当可能数) |
| GET | `/mock/mall-a/orders` | モールAのモック(JSON) |
| GET | `/mock/mall-b/orderList` | モールBのモック(XML) |

更新系は対象受注の `version` を必須で受け取ります(後述)。

---

## 設計上の判断

### 業務ルールはドメイン層だけに置く

```
presentation → application → domain
                    ↑
              infrastructure       (application のインターフェースを実装する)
```

`infrastructure` は `application` が定義したポート(`OrderRepository`、`MallOrderClient`、
`StockRepository`、`TransactionRunner`)を実装します。依存は常に内側を向きます。

`domain` と `application` はフレームワークに依存しません。ユースケースはコンストラクタで
組み立てられる素のクラスで、Bean定義は `infrastructure` 側に置いています。
そのおかげでユースケースのテストはDIコンテナの起動を必要とせず、一瞬で回ります。

### 状態遷移は状態機械が唯一の真実

遷移可否の判定は `OrderStatus` の1箇所にしかありません。Controller にもサービスにも
「このステータスなら…」という分岐を置かないことで、判定ロジックの二重化を防いでいます。

在庫への影響も同じ考え方で、遷移の組み合わせを列挙せず
「その状態が引当を抱えているか」という性質から導いています。

```java
public StockEffect stockEffectOf(OrderStatus target) {
    if (!holdsStockAllocation() && target.holdsStockAllocation()) return StockEffect.ALLOCATE;
    if (holdsStockAllocation() && !target.holdsStockAllocation()) {
        return target == SHIPPED ? StockEffect.SHIP_OUT : StockEffect.RELEASE;
    }
    return StockEffect.NONE;
}
```

遷移を1本追加するたびに在庫側の対応表を書き足す形にすると、書き漏らしたときに在庫だけが狂います。

### 冪等キーはDB制約を最終防衛線にする

`(mall_id, mall_order_number)` にユニーク制約を張り、取込処理はこの制約違反を
**正常系(既に取込済みのためスキップ)** として扱います。

取込は「前回より長く遡って取得し、重複は弾かせる」方式です。前回実行時刻を保存する
差分取得は、実行失敗時や複数インスタンス運用で「どこまで取得済みか」の管理が破綻しやすく、
取りこぼしより重複を選ぶ方が壊れにくいと判断しました。

アプリ側の存在チェックだけに頼らないのは、バッチ多重起動時にチェックとINSERTの間へ
別プロセスが割り込みうるためです。

### 楽観ロックと悲観ロックを使い分ける

| | 受注 | 在庫 |
|---|---|---|
| 方式 | **楽観ロック**(version) | **悲観ロック**(`SELECT ... FOR UPDATE`) |
| 理由 | 同じ受注を2人が同時に触るのは稀。競合したら操作を失敗させ、画面を更新させる方が安全 | 同一商品への同時更新が構造的に多い。競合のたびに失敗させると呼び出し側に再試行の責任が生まれる |

受注の更新系APIは、クライアントが表示していた `version` を必須で受け取ります。
省略を許すと「今のDBの状態に無条件で適用する」意味になり、楽観ロックが機能しなくなるためです。
古い `version` での操作は `409 Conflict` で弾かれます。

在庫の引当は**商品コード順**にロックを取ります。複数の受注が同じ商品群を同時に処理しても
ロック順が揃うため、互いのロックを待ち合うデッドロックを避けられます。

### 受注の更新と在庫の反映は一体で成立させる

「確認済だが在庫は押さえられていない」「引当済だが受注は未確認」という状態は、
実在庫と帳簿の食い違いに直結します。両者は必ず同一トランザクションで行います。

ただし `application` をフレームワーク非依存に保つため、`@Transactional` ではなく
`TransactionRunner` ポートとして境界を宣言し、Spring への依存は実装側に閉じています。

在庫不足で引当に失敗すると、受注のステータス更新ごと巻き戻ります。

```
$ curl -X POST .../api/orders/2/confirmation -d '{"version":0}'
409 {"title":"処理できない要求です","detail":"在庫が不足しています: 商品=SKU-002 要求=2 引当可能=1"}

→ 受注2 は NEW のまま / SKU-002 の引当済も 0 のまま
```

### 在庫は2つの数で持つ

在庫は「実在庫(倉庫にある数)」と「引当済(出荷が約束された数)」を持ち、
引当可能数は保存せず両者から導出します。3つ持つと片方だけ更新された不整合が起きうるためです。

引当は倉庫からモノを出す操作ではないので実在庫を減らしません。出荷して初めて減ります。
この区別が崩れると、売れる数を読み違えて欠品か二重売りを起こします。

### 一覧に集約を返さない

受注一覧は `Order` 集約ではなく、表示に必要な列だけを持つ読み取りモデルを返します。
集約は明細を必ず伴うため、一覧で復元すると画面に出さない明細のロードが件数分走ります。
参照用ポート(`OrderSearchQuery`)を更新用ポート(`OrderRepository`)と分け、
読み書きの非対称を型として残しています。

### エラーの表現

業務ルール違反は `DomainException` のサブクラスで表し、HTTPステータスへの変換は
presentation 層の1箇所(`ApiExceptionHandler`)に集約しています。
例外クラスに `@ResponseStatus` を付けるとドメイン層がSpring MVCに依存するため採っていません。

| 状況 | ステータス |
|---|---|
| リクエストの形式・値が不正 | 400 |
| 受注が存在しない | 404 |
| 要求は正しいが受注の現在状態と衝突(不正な遷移・在庫不足・同時更新) | 409 |

400と409を分けているのは、後者が**同じ要求でも状態次第で成功しうる**ためです。

---

## テスト

```bash
./gradlew test     # Docker が必要(Testcontainers が実PostgreSQLを起動します)
```

| 層 | 方針 |
|---|---|
| domain | Springコンテキスト不要の単体テスト。最速で回る |
| application | ユースケースの仕様。テストダブルは手書きのFake |
| infrastructure | **Testcontainers で実PostgreSQL**を使う |
| presentation | `@WebMvcTest` でHTTP契約(ステータスコード・JSON)を固定 |

### H2 での代用を禁止している理由

インメモリDBは制約違反時の例外や型の丸めが実DBと異なり、
**「テストは通るが本番で壊れる」**状態を作ります。

このリポジトリでは、次のような実DBでしか確認できない挙動をテストで固定しています。

- ユニーク制約違反が `DataIntegrityViolationException` として上がること(冪等キー)
- `CHECK` 制約が「引当済 > 実在庫」の在庫を拒否すること
- `TIMESTAMPTZ` / `NUMERIC` がJavaの型と往復すること
- 在庫不足時に**トランザクションが実際に巻き戻る**こと

カバレッジ目標は設けていません。「テストを読めば仕様が分かる」ことを優先しています。

---

## 技術スタック

- Java 21 / Spring Boot 3.5(Web, Data JPA, Security, Validation)
- PostgreSQL 16 + Flyway
- Gradle (Kotlin DSL)
- JUnit 5 / AssertJ / Testcontainers

---

## 現在の状態

実装済み:

- モールA/Bからの受注取込(定期実行・冪等)
- 受注のライフサイクル管理(確認・出荷指示・出荷完了・キャンセル・返品)
- 在庫引当(引当・解除・出荷確定、悲観ロック、トランザクション境界)
- 受注・在庫の参照API

未実装(今後):

- **認証** — `SecurityConfig` は現在すべてのエンドポイントを開放しています。
  Spring Security を後から追加するとフィルタ順序やCSRF・セッション方針の見直しが
  広範囲に及ぶため、依存だけ最初から入れ、「意図的に開放している」ことをコードに残しています。
- E2Eテスト(Playwright、主要フロー1本)

---

## やらないこと(スコープ外)

- 実在モールのAPI仕様の模倣・転載はしません。モックは「モールA(JSON)」「モールB(XML+独自ステータスコード)」と抽象化しています
- 凝った管理画面UI、決済連携、配送業者API連携は作りません
- 入荷・棚卸などの在庫の入力側、部分出荷、多通貨対応もスコープ外です
