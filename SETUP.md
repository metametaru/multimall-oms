# 開発環境の準備

動かすだけなら [README の「動かす」](README.md#動かす) で足ります。
このファイルは、テストまで含めて手元で回すときに必要な情報をまとめたものです。

## 必要なもの

| | 用途 |
|---|---|
| JDK 21 | ビルドと実行 |
| Docker | PostgreSQL の起動、テスト時の Testcontainers |

Gradle は Wrapper が同梱されているため、別途の導入は不要です。

## 起動

```bash
docker compose up -d      # PostgreSQL 16
./gradlew bootRun         # Flyway がスキーマと初期データを作成する
```

`http://localhost:8080/` でオペレーター画面が開きます。

## テスト

```bash
./gradlew test
```

**Docker が起動している必要があります。** テストは以下を実際に立ち上げます。

- **PostgreSQL**(Testcontainers) — H2 で代用しない理由は [README](README.md#h2-での代用を禁止している理由) を参照
- **Chromium**(Playwright) — 画面E2Eで使用。初回のみ取得に数十秒かかります

ブラウザは Chromium だけを取得します(既定では Firefox / WebKit まで入って約1GB になるため)。
取得は `installPlaywrightBrowser` タスクがビルドの一部として行うので、手動の準備は要りません。

## プロファイル

| プロファイル | 使う場面 | 有効になるもの |
|---|---|---|
| `mock` | ローカル開発(既定) | モールAPIのモック、デモ用利用者の起動時投入 |
| `test` | テスト | 取込スケジューラを止める(テストが自分のタイミングで取込を呼ぶため) |

本番相当の構成では `mock` を外します。モックのエンドポイントもデモ用利用者も存在しなくなります。

## 環境変数

| 変数 | 未設定時の挙動 |
|---|---|
| `MINIOMS_SECURITY_JWT_SECRET` | 署名鍵を起動ごとにランダム生成し、警告を出す(再起動で既存トークンは無効) |

本番相当の構成では 32 バイト以上の値を必ず指定してください。短い値は起動時に弾かれます。

## Windows での注意

- **`curl`** は PowerShell では `Invoke-WebRequest` のエイリアスです。README のコマンドを試すときは
  `curl.exe` を使い、ボディは `-d "{\"version\":0}"` のようにエスケープしてください
- **Testcontainers が Docker を見つけられない場合**、`DOCKER_HOST` を未設定のままにして
  名前付きパイプ経由で接続させるのが基本です。TCP でデーモンを公開する設定は必要ありません
- **`bootRun` をバックグラウンドで動かした場合**、Gradle を止めても Java の子プロセスが
  8080 を掴んだまま残ることがあります。`Get-NetTCPConnection -LocalPort 8080 -State Listen` で
  PID を調べて停止してください

## デモ用の利用者

`mock` プロファイルの起動時に作られます。**ローカル専用**で、本番相当の構成では投入されません。

| ユーザー名 | パスワード | ロール |
|---|---|---|
| `operator` | `operator-pass` | OPERATOR(参照 + 操作) |
| `viewer` | `viewer-pass` | VIEWER(参照のみ) |

パスワードのハッシュはマイグレーションに含めていません。公開リポジトリに残すと、
環境を問わず同じ資格情報が配られることになるためです。
