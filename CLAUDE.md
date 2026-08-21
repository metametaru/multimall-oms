# mini-oms

マルチモール受注統合ミニOMS。転職ポートフォリオ用の公開リポジトリ。
複数ECモール(モックAPI)から受注を取り込み、ステータス管理・在庫引当・出荷確定を行う。

## 技術スタック

- Java 21 / Spring Boot 3.x(Web, Data JPA, Security, Validation)
- PostgreSQL 16 + Flyway
- Gradle (Kotlin DSL)
- テスト: JUnit 5 + Testcontainers + Playwright(E2E は実サーバ起動＋実ブラウザ)
- Docker Compose で `docker compose up` 一発起動

## 文書哲学(このリポジトリの最重要ルール)

| 場所 | 表現するもの |
|---|---|
| コード | **How** — 実装の詳細。読めば動きが分かる状態を保つ |
| テスト | **What** — 仕様。テスト名とアサーションを読めば業務ルールが分かる |
| コミット | **Why** — なぜこの変更をしたか。業務背景・設計判断を書く |
| コメント | **Why not** — なぜ他の選択肢を採らなかったか。自明なコメントは書かない |

- テストメソッド名は日本語可。仕様書として読めることを優先する
  例: `出荷指示済みの受注はキャンセルできない()`
- コミットメッセージ: 1行目に要約、本文に Why を書く。`feat:` `fix:` `test:` `docs:` `refactor:` プレフィックスを使用
- 「何をしたか」だけのコミットメッセージは禁止(diffを見れば分かるため)

## パッケージ構造(レイヤード)

```
com.minioms
├── domain          // エンティティ・値オブジェクト・業務ルール。フレームワーク非依存を保つ
│   └── order       // Order, OrderStatus, 状態遷移ルール
├── application     // ユースケース(受注取込、出荷指示、キャンセル)
├── infrastructure  // JPA実装、外部モールAPIクライアント、スケジューラ
└── presentation    // REST Controller, リクエスト/レスポンスDTO
```

- 依存方向は presentation → application → domain。infrastructure は application のインターフェースを実装する
- **業務ルール(状態遷移可否、キャンセル可能条件、冪等性)は必ず domain 層に置く**。Controller や Service に業務判断を書かない

## コーディングルール

- 状態遷移は `OrderStatus` の状態機械を唯一の真実とする。遷移可否の判定ロジックを他の場所に複製しない
- 冪等キー: `(mall_id, mall_order_number)` のユニーク制約。取込処理はこの制約違反を「正常系(スキップ)」として扱う
- 例外: 業務ルール違反は `DomainException` のサブクラス。HTTPステータスへの変換は presentation 層で行う
- Lombok は `@RequiredArgsConstructor` 程度に留める(過度なマジックを避ける)

## テストルール

- domain 層: 純粋な単体テスト(Spring コンテキスト不要)。最速で回ること
- infrastructure 層: Testcontainers で実 PostgreSQL を使う(H2 での代用禁止 — 実DBとの差異で本番事故を起こした経験則をREADMEに記載)
- 新しい業務ルールを実装する前に、まずテストで仕様を表現する
- カバレッジ目標は設けない。「テストを読めば仕様が分かる」ことを優先する

## やらないこと(スコープ外・コンプライアンス)

- 実在モール(楽天・Qoo10等)のAPI仕様の模倣・転載はしない。モックは「モールA(JSON形式)」「モールB(XML+独自ステータスコード)」と抽象化する
- 現職のコード・テーブル設計・処理ロジックの流用はしない
- 決済連携、配送業者API連携は作らない
- 管理画面は「主要フローを触れる最小の1画面」まで。ビルド環境を要するフロントエンド
  フレームワークは導入せず、静的ファイル3枚(html/css/js)に収める

## Claude Code への指示

- 実装前に該当する domain のテストを先に提示すること
- マイグレーションファイルには、インデックス・制約の設計意図をコメントで残すこと
- コミットメッセージ案には必ず Why(業務背景または設計判断)を含めること
