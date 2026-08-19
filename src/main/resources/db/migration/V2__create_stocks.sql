-- V2: 在庫(引当管理)
-- 設計意図はこのファイルのコメントに残す(CLAUDE.md の文書哲学に従う)

CREATE TABLE stocks (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- order_items.product_code と同じ商品コード体系。
    -- 商品マスタ(products)は作らない: 名称・価格はモールから受注ごとに送られてくるため、
    -- OMSが持つべきなのは「引当可能かどうか」だけであり、マスタを持つと二重管理になる。
    product_code        VARCHAR(64) NOT NULL UNIQUE,
    quantity_on_hand    INT         NOT NULL,  -- 実在庫(倉庫にある数)
    quantity_allocated  INT         NOT NULL DEFAULT 0,  -- 引当済(出荷が約束された数)
    version             BIGINT      NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 在庫の数え方が壊れた状態をDBに残さないための最終防衛線。
    -- 同じ不変条件はドメイン(Stock)でも守っているが、アプリの不具合や
    -- 手作業のSQLで壊れた在庫が入ると、原因の特定が極端に難しくなるため二重に守る。
    CONSTRAINT chk_stocks_quantities CHECK (
        quantity_on_hand >= 0
        AND quantity_allocated >= 0
        AND quantity_allocated <= quantity_on_hand
    )
);

-- 引当は「商品コードで1行を引いて更新する」アクセスしかしないため、
-- UNIQUE制約に付随するインデックスで足りる(追加のインデックスは張らない)。

-- 初期データ: モックモールが返す商品の在庫。
-- 引当の成功・失敗の両方を試せるよう、数量にばらつきを持たせている。
INSERT INTO stocks (product_code, quantity_on_hand) VALUES
    ('SKU-001', 10),
    ('SKU-002', 8),
    ('SKU-777', 6),
    ('SKU-888', 4),
    ('SKU-999', 2);
