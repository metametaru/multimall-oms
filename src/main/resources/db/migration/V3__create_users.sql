-- V3: 利用者(認証・認可)
-- 設計意図はこのファイルのコメントに残す(CLAUDE.md の文書哲学に従う)

CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- ログインに使う識別子。同じ名前の利用者が二重にできると、
    -- どちらの権限で認証されるかが登録順に依存して不定になる
    username       VARCHAR(50)  NOT NULL UNIQUE,
    -- BCryptハッシュ($2a$ 形式で60文字)。平文は保存しない。
    -- 桁数に余裕を持たせているのは、将来ハッシュ方式を変えたときに
    -- 列定義の変更を伴わずに済ませるため
    password_hash  VARCHAR(72)  NOT NULL,
    -- Role enum名を格納。OPERATOR=受注と在庫を動かせる / VIEWER=参照のみ
    role           VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 未知のロールが入ると、認可の判定で「権限が無い」のか「設定ミス」なのかを
    -- 区別できない。アプリのenumと同じ集合をDB側でも固定して、
    -- 綴り間違いが静かな認可の穴にならないようにする。
    CONSTRAINT chk_users_role CHECK (role IN ('OPERATOR', 'VIEWER'))
);

-- 認証時のアクセスは「ユーザー名で1行を引く」だけのため、
-- UNIQUE制約に付随するインデックスで足りる(追加のインデックスは張らない)。

-- 初期データは置かない。
-- パスワードハッシュをマイグレーションに書くと、公開リポジトリでは
-- 全員が知る資格情報になり、環境を問わず同じ鍵が配られてしまう。
-- ローカルデモ用の利用者は mock プロファイルの起動時に生成する(DemoUserSeeder)。
