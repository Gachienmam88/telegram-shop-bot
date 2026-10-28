# Telegram Shop Bot

Bot Telegram bán tài khoản/key tự động 24/7: đa ngôn ngữ (VI/EN/TH/ZH/KO), danh mục → sản phẩm → mua bằng ví, chuyển khoản VietQR (SePay tự xác nhận) hoặc USDT (BSC/TRON/Polygon, tự xác nhận on-chain), giao hàng tự động, ví + lịch sử số dư, affiliate, thông báo hàng về. Sau khi chọn ngôn ngữ có 2 lựa chọn: **mua tài khoản AI** hoặc **bán acc TikTok Shop** (thu mua, admin duyệt tay). Có **rút tiền** từ ví (không chiết khấu).

**Stack**: Java 17 · Spring Boot 3.5 · TelegramBots 10 (long polling) · PostgreSQL 16 · Flyway · Docker Compose · Cloudflare Tunnel

## Tính năng

| Khách hàng | Admin (lệnh Telegram) |
|---|---|
| Chọn ngôn ngữ, trang chủ menu inline | `/addcat`, `/addproduct`, `/setdesc`, `/setprice`, `/hide` |
| Danh mục 2 cột + SP bán chạy, Làm mới | `/addstock` (dán text hoặc file .txt) → tự thông báo hàng về |
| Chi tiết SP: giá, tồn, đã bán, mô tả | `/plist` xem SP kèm id · `/confirm` xác nhận thanh toán tay |
| Nhập số lượng (validate 1..tồn kho) | `/addbal` cộng/trừ ví khách |
| Thanh toán ví hoặc QR; đơn QR tự hủy sau 5 phút, xóa tin QR | `/setaff` bật hoa hồng affiliate (mặc định tắt) |
| Nạp ví QR, lịch sử biến động số dư, lịch sử mua | `/stats` doanh thu |
| Mua / nạp ví bằng USDT (BSC, TRON, Polygon), tự nhận tiền | Giao dịch USDT không khớp → bot báo admin kèm link tx |
| Link giới thiệu (affiliate) | `/wipechats XACNHAN` xóa chat (2 phía) với mọi khách, tin dưới 48h |
| Bán acc TikTok: nhập email/SĐT + username + giá (không nhập mật khẩu) | Hồ sơ bán acc → nút **Duyệt/Từ chối** (duyệt = cộng giá khách đề nghị vào ví) |
| Rút tiền: lưu TK ngân hàng 1 lần, nhập số tiền → chờ duyệt | Yêu cầu rút → nút **Đã chuyển/Từ chối** (từ chối = hoàn tiền); khách bấm xác nhận đã nhận |
|  | Gõ `/admin` để xem đầy đủ |

## Chạy local (dev)

Cần: Java 17, Maven, Docker.

```bash
cp .env.example .env          # điền BOT_TOKEN, BOT_USERNAME, ADMIN_IDS, BANK_*, SEPAY_API_KEY
docker compose up -d          # PostgreSQL dev
mvn spring-boot:run
```

Test (Testcontainers tự bật PostgreSQL, cần Docker):

```bash
mvn test
```

Thử webhook SePay ở local:

```bash
curl -X POST http://localhost:8080/sepay/webhook \
  -H "Authorization: Apikey <SEPAY_API_KEY>" -H "Content-Type: application/json" \
  -d '{"id":1,"transferType":"in","transferAmount":100000,"content":"NAPXXXXXXXX"}'
```

## Bắt đầu bán hàng

```
/addcat ✂️ | CapCut
/addproduct 1 | ✂️ | CapCut Pro 1 tháng | 220000
<b>Bảo hành 30 ngày</b>
Định dạng: Tài khoản|Mật khẩu
/addstock 1
user1@mail.com|pass1
user2@mail.com|pass2
```

## Deploy

Xem [docs/deployment-guide.md](docs/deployment-guide.md).

## Cấu trúc

```
src/main/java/com/shopbot/
  config/      ShopProperties (.env → application.yml), Telegram client
  domain/      Entity JPA + enum
  repository/  Spring Data JPA
  service/     Nghiệp vụ: đơn hàng, ví, kho, khớp thanh toán, affiliate
  telegram/    ShopBot (long polling), UpdateRouter, handler/, keyboard/, admin/
  service/crypto/  USDT: đọc giao dịch on-chain (RPC / TronGrid), khớp invoice
  scheduler/   Hủy đơn / nạp quá hạn, quét giao dịch USDT mỗi 15s
  web/         SePay webhook
src/main/resources/
  db/migration/  Flyway
  i18n/          messages*.properties (VI gốc, EN, TH, ZH, KO)
```

## Nguyên tắc tiền
- Mọi thay đổi số dư đi qua `WalletService` (khóa dòng user + ghi `balance_logs`).
- Tiền khách chuyển **không bao giờ mất**: chuyển thiếu / trễ / không khớp đơn → cộng vào ví (hoặc báo admin nếu không xác định được khách).
- Webhook trùng (cùng id SePay) chỉ xử lý 1 lần.
- USDT: số tiền **tròn** (giá VNĐ / `USDT_RATE`, làm tròn lên 2 chữ số; nạp 10 USDT → chuyển đúng 10), khách tự trả phí mạng để ví shop nhận đúng số đó. Trên 1 mạng, 1 số tiền chỉ giữ cho 1 đơn/nạp chưa trả (đến hạn + 10 phút) → nhận diện giao dịch; khách thứ 2 trùng số tiền được mời chọn mạng khác. Chỉ nhận đúng contract USDT, chờ đủ xác nhận, chống trùng theo tx. Bot chỉ cần địa chỉ ví, **không cần private key**.
- USDT lệch số tiền: nếu chỉ có 1 invoice đang mở cùng mạng và số nhận trong 50–150% → quy đổi VNĐ vào đơn đó (thiếu: hủy + cộng ví; dư: giao + cộng phần dư). Còn lại → báo admin.
- Giữ hàng bằng `SELECT ... FOR UPDATE SKIP LOCKED` → không bán trùng tài khoản.
