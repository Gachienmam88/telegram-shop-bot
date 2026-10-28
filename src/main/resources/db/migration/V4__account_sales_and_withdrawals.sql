-- Thu mua tài khoản TikTok (không giữ mật khẩu, chỉ email/SĐT + username) và rút tiền từ ví.

-- Thông tin nhận tiền của người dùng (nhập 1 lần, dùng lại khi rút).
ALTER TABLE users ADD COLUMN bank_name           VARCHAR(100);
ALTER TABLE users ADD COLUMN bank_account_number VARCHAR(40);
ALTER TABLE users ADD COLUMN bank_account_holder VARCHAR(100);

-- Tài khoản TikTok người dùng gửi bán. status: PENDING | ACCEPTED | REJECTED
CREATE TABLE account_submissions (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(16)  NOT NULL UNIQUE,
    user_id          BIGINT       NOT NULL REFERENCES users (user_id),
    contact          VARCHAR(200) NOT NULL,
    account_username VARCHAR(100) NOT NULL,
    asking_price     BIGINT       NOT NULL CHECK (asking_price > 0),
    status           VARCHAR(16)  NOT NULL,
    chat_id          BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    decided_at       TIMESTAMPTZ,
    decided_by       BIGINT
);
CREATE INDEX idx_submissions_user ON account_submissions (user_id, created_at DESC);
CREATE INDEX idx_submissions_status ON account_submissions (status);

-- Yêu cầu rút tiền. status: PENDING | PAID | CONFIRMED | REJECTED
-- Thông tin ngân hàng được chụp lại tại thời điểm tạo yêu cầu (phòng khi user đổi về sau).
CREATE TABLE withdrawals (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(16)  NOT NULL UNIQUE,
    user_id              BIGINT       NOT NULL REFERENCES users (user_id),
    amount               BIGINT       NOT NULL CHECK (amount > 0),
    bank_name            VARCHAR(100) NOT NULL,
    bank_account_number  VARCHAR(40)  NOT NULL,
    bank_account_holder  VARCHAR(100) NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    chat_id              BIGINT       NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    paid_at              TIMESTAMPTZ,
    confirmed_at         TIMESTAMPTZ,
    decided_by           BIGINT
);
CREATE INDEX idx_withdrawals_user ON withdrawals (user_id, created_at DESC);
CREATE INDEX idx_withdrawals_status ON withdrawals (status);
