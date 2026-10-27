-- =====================================================================
-- StockFlow - esquema inicial
--
-- Filosofia: as regras de integridade ficam NO BANCO (CHECK, UNIQUE, FK
-- e triggers), e não apenas na aplicação. Se um bug (ou uma condição de
-- corrida) tentar violar uma regra, o banco recusa a operação.
-- =====================================================================

CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_role     CHECK (role IN ('ADMIN', 'OPERATOR'))
);

CREATE TABLE category (
    id   BIGSERIAL   PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT uq_category_name UNIQUE (name)
);

CREATE TABLE supplier (
    id    BIGSERIAL    PRIMARY KEY,
    name  VARCHAR(120) NOT NULL,
    email VARCHAR(120),
    phone VARCHAR(40)
);

CREATE TABLE product (
    id             BIGSERIAL     PRIMARY KEY,
    sku            VARCHAR(40)   NOT NULL,
    name           VARCHAR(150)  NOT NULL,
    category_id    BIGINT        REFERENCES category (id),
    supplier_id    BIGINT        REFERENCES supplier (id),
    cost_price     NUMERIC(12,2) NOT NULL,
    sale_price     NUMERIC(12,2) NOT NULL,
    stock_quantity INTEGER       NOT NULL DEFAULT 0,
    min_stock      INTEGER       NOT NULL DEFAULT 0,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_sku                UNIQUE (sku),
    CONSTRAINT ck_product_cost_price         CHECK (cost_price >= 0),
    CONSTRAINT ck_product_sale_price         CHECK (sale_price >= 0),
    -- Última linha de defesa contra estoque negativo, mesmo que a aplicação falhe:
    CONSTRAINT ck_product_stock_non_negative CHECK (stock_quantity >= 0),
    CONSTRAINT ck_product_min_stock          CHECK (min_stock >= 0)
);
CREATE INDEX ix_product_category ON product (category_id);
CREATE INDEX ix_product_supplier ON product (supplier_id);

CREATE TABLE price_history (
    id             BIGSERIAL     PRIMARY KEY,
    product_id     BIGINT        NOT NULL REFERENCES product (id),
    old_cost_price NUMERIC(12,2),
    new_cost_price NUMERIC(12,2) NOT NULL,
    old_sale_price NUMERIC(12,2),
    new_sale_price NUMERIC(12,2) NOT NULL,
    changed_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    changed_by     VARCHAR(60)   NOT NULL
);
CREATE INDEX ix_price_history_product ON price_history (product_id, changed_at);

CREATE TABLE sale (
    id              BIGSERIAL     PRIMARY KEY,
    status          VARCHAR(20)   NOT NULL,
    total           NUMERIC(14,2) NOT NULL DEFAULT 0,
    -- Idempotência: a mesma Idempotency-Key nunca gera duas vendas.
    idempotency_key VARCHAR(100)  NOT NULL,
    request_hash    VARCHAR(64)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(60)   NOT NULL,
    cancelled_at    TIMESTAMPTZ,
    cancelled_by    VARCHAR(60),
    cancel_reason   VARCHAR(255),
    CONSTRAINT uq_sale_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_sale_status          CHECK (status IN ('COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_sale_total           CHECK (total >= 0),
    CONSTRAINT ck_sale_cancel_fields   CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)
        OR (status = 'COMPLETED' AND cancelled_at IS NULL)
    )
);
CREATE INDEX ix_sale_created_at ON sale (created_at);
CREATE INDEX ix_sale_status     ON sale (status);

CREATE TABLE sale_item (
    id         BIGSERIAL     PRIMARY KEY,
    sale_id    BIGINT        NOT NULL REFERENCES sale (id),
    product_id BIGINT        NOT NULL REFERENCES product (id),
    quantity   INTEGER       NOT NULL,
    -- Preço e custo são "fotografados" no momento da venda (não mudam se o produto mudar depois).
    unit_price NUMERIC(12,2) NOT NULL,
    unit_cost  NUMERIC(12,2) NOT NULL,
    line_total NUMERIC(14,2) NOT NULL,
    CONSTRAINT ck_sale_item_quantity   CHECK (quantity > 0),
    CONSTRAINT ck_sale_item_line_total CHECK (line_total >= 0),
    CONSTRAINT uq_sale_item_product    UNIQUE (sale_id, product_id)
);
CREATE INDEX ix_sale_item_product ON sale_item (product_id);

-- Livro-razão do estoque: toda variação de estoque gera uma linha.
-- quantity é a VARIAÇÃO (positiva = entrada, negativa = saída).
CREATE TABLE stock_movement (
    id            BIGSERIAL    PRIMARY KEY,
    product_id    BIGINT       NOT NULL REFERENCES product (id),
    type          VARCHAR(20)  NOT NULL,
    quantity      INTEGER      NOT NULL,
    balance_after INTEGER      NOT NULL,
    reason        VARCHAR(255),
    sale_id       BIGINT       REFERENCES sale (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(60)  NOT NULL,
    CONSTRAINT ck_stock_movement_type          CHECK (type IN ('IN', 'OUT', 'ADJUSTMENT', 'SALE', 'SALE_CANCEL')),
    CONSTRAINT ck_stock_movement_quantity      CHECK (quantity <> 0),
    CONSTRAINT ck_stock_movement_balance_after CHECK (balance_after >= 0),
    -- Entradas somam; saídas subtraem. Ajuste pode ser qualquer sinal.
    CONSTRAINT ck_stock_movement_sign CHECK (
        (type IN ('IN', 'SALE_CANCEL') AND quantity > 0)
        OR (type IN ('OUT', 'SALE') AND quantity < 0)
        OR type = 'ADJUSTMENT'
    )
);
CREATE INDEX ix_stock_movement_product ON stock_movement (product_id, created_at);
CREATE INDEX ix_stock_movement_sale    ON stock_movement (sale_id);

-- ---------------------------------------------------------------------
-- Auditoria: tabelas "append-only". Linhas de histórico nunca são
-- alteradas nem apagadas. Correções são feitas com NOVOS lançamentos.
-- ---------------------------------------------------------------------
CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A tabela % é append-only (auditoria): % não é permitido', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stock_movement_append_only
    BEFORE UPDATE OR DELETE ON stock_movement
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER trg_price_history_append_only
    BEFORE UPDATE OR DELETE ON price_history
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER trg_sale_item_append_only
    BEFORE UPDATE OR DELETE ON sale_item
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Vendas nunca são apagadas; são CANCELADAS (com reversão do estoque).
CREATE FUNCTION forbid_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'A tabela % não permite DELETE: cancele a venda em vez de apagá-la', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sale_no_delete
    BEFORE DELETE ON sale
    FOR EACH ROW EXECUTE FUNCTION forbid_delete();
