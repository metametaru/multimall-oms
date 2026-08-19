-- V1: 初期スキーマ(モール・受注・受注明細)
-- 設計意図はこのファイルのコメントに残す(CLAUDE.md の文書哲学に従う)

CREATE TABLE malls (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,  -- 'MALL_A', 'MALL_B' など。アプリ側の識別子
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mall_id            BIGINT       NOT NULL REFERENCES malls (id),
    mall_order_number  VARCHAR(64)  NOT NULL,  -- モール側の注文番号(形式はモールごとに異なるため文字列)
    status             VARCHAR(30)  NOT NULL,  -- OrderStatus enum名を格納
    customer_name      VARCHAR(100) NOT NULL,
    total_amount       NUMERIC(12, 0) NOT NULL,  -- 日本円前提のため小数なし。多通貨対応はスコープ外
    ordered_at         TIMESTAMPTZ  NOT NULL,   -- モール側の注文日時
    imported_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),  -- 取込日時(取込遅延の監視に使う)
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,  -- 楽観ロック用(JPA @Version)

    -- 冪等キー: 同一モールの同一注文番号は二重取込しない。
    -- 取込バッチはこの制約違反を「既取込のためスキップ(正常系)」として扱う。
    -- アプリ側の existsBy チェックだけに頼らないのは、バッチ多重起動時の
    -- レースコンディションをDB層で最終防衛するため。
    CONSTRAINT uq_orders_mall_order UNIQUE (mall_id, mall_order_number)
);

-- ステータス別の受注一覧(オペレーターの主画面)が最頻クエリになる想定。
-- status 単独ではなく (status, ordered_at) の複合にしているのは、
-- 「未確認の受注を古い順に処理する」というアクセスパターンに合わせるため。
CREATE INDEX idx_orders_status_ordered_at ON orders (status, ordered_at);

CREATE TABLE order_items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      BIGINT       NOT NULL REFERENCES orders (id),
    product_code  VARCHAR(64)  NOT NULL,
    product_name  VARCHAR(200) NOT NULL,
    unit_price    NUMERIC(12, 0) NOT NULL,
    quantity      INT          NOT NULL CHECK (quantity > 0)
);

-- 受注詳細表示時の明細取得用。FK には自動でインデックスが張られない(PostgreSQL)ため明示。
CREATE INDEX idx_order_items_order_id ON order_items (order_id);

-- 初期データ: モックモール2種
INSERT INTO malls (code, name) VALUES
    ('MALL_A', 'モールA'),
    ('MALL_B', 'モールB');
