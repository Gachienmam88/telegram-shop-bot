-- Thanh toán USDT (BSC / TRON / POLYGON). orders.pay_method có thêm giá trị CRYPTO.

CREATE TABLE crypto_invoices (
    id          BIGSERIAL PRIMARY KEY,
    network     VARCHAR(16)    NOT NULL,
    usdt_amount NUMERIC(20, 6) NOT NULL,
    pay_ref     VARCHAR(16)    NOT NULL,
    vnd_amount  BIGINT         NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL,
    expires_at  TIMESTAMPTZ    NOT NULL,
    match_until TIMESTAMPTZ    NOT NULL,
    matched_at  TIMESTAMPTZ
);
CREATE INDEX idx_crypto_invoices_match ON crypto_invoices (network, usdt_amount, match_until);
CREATE INDEX idx_crypto_invoices_pay_ref ON crypto_invoices (pay_ref);

CREATE TABLE crypto_transfers (
    tx_key      VARCHAR(160) PRIMARY KEY,
    network     VARCHAR(16)    NOT NULL,
    tx_hash     VARCHAR(100)   NOT NULL,
    amount      NUMERIC(38, 18) NOT NULL,
    matched_ref VARCHAR(16),
    created_at  TIMESTAMPTZ    NOT NULL
);
