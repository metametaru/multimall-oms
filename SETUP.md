# Day 1–2 セットアップ手順

## 1. プロジェクト生成(Spring Initializr)

https://start.spring.io で以下を選択して生成:

- Project: **Gradle - Kotlin DSL** / Language: **Java** / Java: **21**
- Spring Boot: 3.x 最新安定版
- Dependencies: **Spring Web / Spring Data JPA / Spring Security / Validation / Flyway Migration / PostgreSQL Driver / Lombok / Testcontainers**
- Group: `com.minioms` / Artifact: `mini-oms`

生成後、このスターターキットのファイルを対応する場所に配置する
(`build.gradle.kts` は Initializr 生成版のバージョン番号を正としてマージ)。

## 2. application.yml

`src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/minioms
    username: minioms
    password: minioms_local
  jpa:
    hibernate:
      ddl-auto: validate   # スキーマ管理はFlywayに一本化。JPAには検証のみさせる
    open-in-view: false    # OSIVはN+1の温床になるため最初から無効化
```

## 3. 起動確認

```powershell
docker compose up -d
.\gradlew test        # OrderStatusTest が通ることを確認
.\gradlew bootRun     # Flyway V1 が適用されることをログで確認
```

Spring Security を入れた直後は全エンドポイントが認証必須になる。
Day 3–4 の間は一時的に permitAll する SecurityConfig を置き、
第3週の認証実装時に本実装へ差し替える(このWhyもコミットに残す)。

## 4. GitHubリポジトリ作成と最初のコミット

最初のコミットから Why を書く。例:

```
feat: プロジェクト骨格とドメインの状態機械を追加

マルチモール受注統合OMSのポートフォリオ。最初に状態機械から
実装するのは、受注ライフサイクルの業務ルールがこのシステムの
核であり、他の全機能(取込・出荷・キャンセル)が依存するため。
遷移ルールの設計意図は OrderStatus.java のコメントを参照。
```

## Day 1–2 完了条件

- [ ] `docker compose up` でPostgreSQLが起動する
- [ ] `gradlew test` で OrderStatusTest(全ケース)が通る
- [ ] `gradlew bootRun` でFlyway V1が適用される
- [ ] GitHubにpush済み、CLAUDE.md がリポジトリルートにある
- [ ] コミットメッセージに Why が書かれている

## 次(Day 3–4)

- `Order` エンティティ実装(状態遷移メソッドは `OrderStatus.transitionTo` に委譲)
- Testcontainers でリポジトリテスト1本(冪等キーのユニーク制約違反の挙動確認)
