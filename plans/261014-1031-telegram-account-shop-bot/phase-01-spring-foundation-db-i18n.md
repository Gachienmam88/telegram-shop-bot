# Phase 01: Nền tảng — Spring Boot, PostgreSQL, i18n, khung bot

## Mục tiêu
Project Spring Boot chạy được, kết nối PostgreSQL (Docker), Flyway tạo schema, bot nhận `/start`, có router update + lưu trạng thái hội thoại + i18n 4 ngôn ngữ.

## Phụ thuộc
- Không

## Cấu trúc package (`com.shopbot`)
```
config/        BotProperties (@ConfigurationProperties), PaymentProperties, AppConfig
domain/        entity JPA: User, Category, Product, StockItem, Order, Topup, BalanceLog,
               BankTransaction, ConversationState, AppSetting + enums
repository/    Spring Data JPA repositories
service/       nghiệp vụ (phase 2-4)
telegram/      ShopBot (consumer), UpdateRouter, handlers/, keyboards/, TelegramSender
web/           SepayWebhookController (phase 3)
scheduler/     PaymentExpiryScheduler (phase 2)
i18n/          MessageService (wrap MessageSource theo lang của user)
```
Resources: `application.yml`, `db/migration/V1__init_schema.sql`, `i18n/messages_{vi,en,th,zh}.properties`

## Schema (Flyway V1)
- `users(user_id PK bigint, username, lang, balance bigint, referrer_id, created_at)`
- `categories(id, emoji, name, sort_order)`
- `products(id, category_id FK, emoji, name, price bigint, description text, sold int, active bool, created_at)`
- `stock_items(id, product_id FK, content text, status AVAILABLE|RESERVED|SOLD, order_code, created_at)` + index `(product_id, status)`
- `orders(code PK, pay_ref UNIQUE, user_id, product_id, quantity, unit_price, amount, status PENDING|PAID|CANCELLED, pay_method WALLET|BANK, chat_id, payment_message_id, created_at, expires_at, paid_at)`
- `topups(code PK, pay_ref UNIQUE, user_id, amount, status, chat_id, payment_message_id, created_at, expires_at, paid_at)`
- `balance_logs(id, user_id, delta, balance_after, reason, ref, created_at)`
- `bank_transactions(sepay_id PK, amount, content, matched_ref, created_at)` — chống trùng webhook
- `conversation_states(user_id PK, state, data jsonb, updated_at)` — FSM nhập số lượng / số tiền
- `app_settings(key PK, value)` — vd `affiliate_percent = 0`

Tiền lưu kiểu `bigint` (VND, không số lẻ).

## Files sẽ tạo
- `pom.xml` (spring-boot-starter-web, data-jpa, validation, flyway + flyway-database-postgresql, postgresql, telegrambots-springboot-longpolling-starter, telegrambots-client, lombok, test: spring-boot-starter-test, testcontainers-postgresql)
- `docker-compose.yml` (postgres 16 cho dev), `.env.example`, `.gitignore`, `README.md`
- Các class ở cấu trúc trên (khung)

## Cấu hình (.env → application.yml)
`BOT_TOKEN, BOT_USERNAME, ADMIN_IDS, DB_URL/DB_USER/DB_PASSWORD, BANK_BIN/BANK_CODE (MB), BANK_ACCOUNT, BANK_OWNER, SEPAY_API_KEY, NOTIFY_CHAT_ID, SUPPORT_USERNAME, ORDER_TIMEOUT_SECONDS=300, USDT_RATE, TOPUP_MIN=10000, TOPUP_MAX=50000000`

## Implementation steps
1. Kiểm tra version stable mới nhất của Spring Boot 3.x + TelegramBots tương thích Java 17
2. Tạo `pom.xml`, `docker-compose.yml`, `application.yml`
3. Flyway V1 + entities + repositories
4. `MessageService` + 4 file properties (VI đầy đủ, fallback VI)
5. `ShopBot` long polling → `UpdateRouter` (phân luồng message / callback_query / command); `UserContextLoader` upsert user mỗi update
6. `/start` trả lời placeholder

## Todo list
- [x] pom + docker-compose + .env.example + .gitignore
- [x] application.yml + properties classes
- [x] Flyway V1 + entities + repos
- [x] i18n 4 ngôn ngữ
- [x] ShopBot + UpdateRouter + TelegramSender
- [x] ConversationState service

## Definition of Done
- [x] `docker compose up -d postgres` + `mvn spring-boot:run` khởi động không lỗi, Flyway tạo đủ bảng
- [ ] `/start` có phản hồi trên Telegram _(chưa test trên bot thật — cần BOT_TOKEN)_
- [x] `mvn -q compile` pass

## Risks
- Version TelegramBots đổi API giữa các major: pin version cụ thể, đọc doc trước khi code
