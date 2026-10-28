-- Người dùng bot. balance tính bằng VND.
CREATE TABLE users (
    user_id     BIGINT PRIMARY KEY,
    username    VARCHAR(64),
    lang        VARCHAR(8)  NOT NULL DEFAULT 'vi',
    balance     BIGINT      NOT NULL DEFAULT 0 CHECK (balance >= 0),
    referrer_id BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_referrer ON users (referrer_id);

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    emoji      VARCHAR(16)  NOT NULL DEFAULT '',
    name       VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT       NOT NULL REFERENCES categories (id),
    emoji       VARCHAR(16)  NOT NULL DEFAULT '',
    name        VARCHAR(200) NOT NULL,
    price       BIGINT       NOT NULL CHECK (price > 0),
    description TEXT         NOT NULL DEFAULT '',
    sold        INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_category ON products (category_id);

-- Mỗi dòng là 1 tài khoản/key. status: AVAILABLE | RESERVED | SOLD
CREATE TABLE stock_items (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES products (id),
    content    TEXT        NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    order_code VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_product_status ON stock_items (product_id, status);
CREATE INDEX idx_stock_order_code ON stock_items (order_code);

-- status: PENDING | PAID | CANCELLED ; pay_method: WALLET | BANK
CREATE TABLE orders (
    code               VARCHAR(40) PRIMARY KEY,
    pay_ref            VARCHAR(16)  NOT NULL UNIQUE,
    user_id            BIGINT       NOT NULL REFERENCES users (user_id),
    product_id         BIGINT       NOT NULL REFERENCES products (id),
    product_name       VARCHAR(200) NOT NULL,
    quantity           INT          NOT NULL,
    unit_price         BIGINT       NOT NULL,
    amount             BIGINT       NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    pay_method         VARCHAR(16)  NOT NULL,
    chat_id            BIGINT       NOT NULL,
    payment_message_id INT,
    created_at         TIMESTAMPTZ  NOT NULL,
    expires_at         TIMESTAMPTZ,
    paid_at            TIMESTAMPTZ
);
CREATE INDEX idx_orders_status_expires ON orders (status, expires_at);
CREATE INDEX idx_orders_user_paid ON orders (user_id, paid_at DESC);

-- status: PENDING | PAID | EXPIRED
CREATE TABLE topups (
    code               VARCHAR(40) PRIMARY KEY,
    pay_ref            VARCHAR(16) NOT NULL UNIQUE,
    user_id            BIGINT      NOT NULL REFERENCES users (user_id),
    amount             BIGINT      NOT NULL,
    paid_amount        BIGINT,
    status             VARCHAR(16) NOT NULL,
    chat_id            BIGINT      NOT NULL,
    payment_message_id INT,
    created_at         TIMESTAMPTZ NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    paid_at            TIMESTAMPTZ
);
CREATE INDEX idx_topups_status_expires ON topups (status, expires_at);

CREATE TABLE balance_logs (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (user_id),
    delta         BIGINT      NOT NULL,
    balance_after BIGINT      NOT NULL,
    reason        VARCHAR(20) NOT NULL,
    ref           VARCHAR(60),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_balance_logs_user ON balance_logs (user_id, id DESC);

-- Giao dịch SePay đã xử lý, chống xử lý trùng khi webhook gửi lại.
CREATE TABLE bank_transactions (
    sepay_id    BIGINT PRIMARY KEY,
    amount      BIGINT      NOT NULL,
    content     TEXT,
    matched_ref VARCHAR(16),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Trạng thái hội thoại (đang chờ nhập số lượng / số tiền nạp ...).
CREATE TABLE conversation_states (
    user_id    BIGINT PRIMARY KEY,
    state      VARCHAR(32) NOT NULL,
    data       VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_settings (
    key   VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);
INSERT INTO app_settings (key, value) VALUES ('affiliate_percent', '0');
