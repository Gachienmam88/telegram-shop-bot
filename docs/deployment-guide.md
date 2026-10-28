# Hướng dẫn deploy lên VPS Ubuntu

Stack chạy bằng Docker Compose: **PostgreSQL** + **bot** (Spring Boot) + **cloudflared** (đưa webhook SePay ra internet qua HTTPS, không cần mở port).

## 1. Cài Docker trên VPS

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # đăng xuất / đăng nhập lại
docker compose version
```

## 2. Đưa code lên VPS

```bash
sudo mkdir -p /opt/telegram-shop-bot && sudo chown $USER /opt/telegram-shop-bot
# git clone <repo> /opt/telegram-shop-bot   hoặc   scp -r ./ user@vps:/opt/telegram-shop-bot
cd /opt/telegram-shop-bot
cp .env.example .env && chmod 600 .env
nano .env
```

Các biến **bắt buộc**: `BOT_TOKEN`, `BOT_USERNAME`, `ADMIN_IDS`, `BANK_ACCOUNT_NUMBER`, `BANK_ACCOUNT_NAME`, `SEPAY_API_KEY`, `DB_PASSWORD` (đặt mật khẩu mạnh, khác `shopbot`).

## 3. Cloudflare Tunnel (webhook SePay)

SePay cần một URL HTTPS **cố định** để gửi webhook.

### Cách A — Có domain (khuyến nghị, URL cố định)
1. Mua domain bất kỳ, đổi nameserver về Cloudflare (thêm site trong dashboard Cloudflare).
2. Cloudflare **Zero Trust → Networks → Tunnels → Create tunnel** (loại Cloudflared), đặt tên `shopbot`.
3. Copy **token** của tunnel → dán vào `.env`: `CF_TUNNEL_TOKEN=...`
4. Tab **Public Hostname**: hostname `pay.<domain-cua-ban>` → Service `HTTP` → URL `bot:8080`.
5. Chạy:
   ```bash
   docker compose -f docker-compose.prod.yml --profile tunnel up -d --build
   ```
6. Webhook URL: `https://pay.<domain-cua-ban>/sepay/webhook`

### Cách B — Chưa có domain (CHỈ để test)
```bash
docker compose -f docker-compose.prod.yml --profile quick-tunnel up -d --build
docker compose -f docker-compose.prod.yml logs cloudflared-quick | grep trycloudflare
```
URL dạng `https://xxxx.trycloudflare.com` **đổi mỗi lần container restart** → phải cập nhật lại trên SePay. Trong lúc webhook chưa ổn định, admin dùng `/confirm DHxxxx` để xác nhận tay.

## 4. Cấu hình SePay

1. Đăng ký [sepay.vn](https://sepay.vn), liên kết tài khoản **MB Bank** nhận tiền.
2. Mục **Webhooks → Thêm webhook**:
   - Sự kiện: **Có tiền vào**
   - URL: `https://<hostname>/sepay/webhook`
   - Kiểu chứng thực: **API Key** → nhập đúng giá trị `SEPAY_API_KEY` trong `.env`
3. Bot tìm mã `DHxxxxxxxx` (đơn hàng) / `NAPxxxxxxxx` (nạp tiền) trong nội dung chuyển khoản. Nếu SePay yêu cầu cấu hình "tiền tố mã thanh toán", thêm 2 tiền tố `DH` và `NAP`.

## 4b. Thanh toán USDT (BSC / TRON / Polygon)

Không cần webhook hay tunnel: bot tự đọc blockchain mỗi 15 giây (node công khai miễn phí).

1. Trong `.env` điền **địa chỉ ví nhận** (không bao giờ điền private key / seed):
   `CRYPTO_BSC_ADDRESS`, `CRYPTO_TRON_ADDRESS`, `CRYPTO_POLYGON_ADDRESS`. Để trống = tắt mạng đó.
2. Đặt `USDT_RATE` (1 USDT = ? VNĐ). Đổi tỷ giá → sửa `.env` rồi restart bot.
3. `CRYPTO_TIMEOUT_SECONDS` (mặc định 1800 = 30 phút): thời gian giữ hàng chờ khách chuyển USDT. Tiền đến muộn quá hạn + 10 phút → bot không tự gán, admin nhận cảnh báo kèm link tx để xử lý tay (`/addbal`).
4. Lần chạy đầu bot bắt đầu đọc từ thời điểm hiện tại (không quét lịch sử cũ). Con trỏ lưu trong DB, bot tắt rồi bật lại vẫn đọc bù.
5. Nên dùng ví **riêng cho shop**: tự chuyển tiền vào ví này khi đang có đơn USDT mở có thể bị bot gán nhầm cho đơn đó.
6. TronGrid không key giới hạn ~3 request/giây (đủ dùng). Nếu log báo `rate exceeded`, đăng ký key miễn phí tại trongrid.io → `TRONGRID_API_KEY`.

Log khi nhận tiền: `USDT TRON 4.45 tx=... -> OrderPaid`. Lỗi đọc mạng: `USDT watcher BSC error: ...` (tự thử lại sau 15s).

## 5. Kiểm tra

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f bot
```

- Nhắn `/start` cho bot → hiện chọn ngôn ngữ.
- Admin nhắn `/admin` → hiện danh sách lệnh.
- **Test tiền thật**: nạp ví 10.000đ → bot tự cộng tiền trong vài giây. Log có dòng `Payment sepayId=... -> TopupPaid`.

## 6. Backup database hằng ngày

```bash
chmod +x deploy/backup-db.sh
./deploy/backup-db.sh            # chạy thử
crontab -e
# thêm dòng:
0 3 * * * /opt/telegram-shop-bot/deploy/backup-db.sh >> /var/log/shopbot-backup.log 2>&1
```

Khôi phục:
```bash
gunzip -c backups/shopbot-YYYYMMDD-HHMMSS.sql.gz | docker compose -f docker-compose.prod.yml exec -T postgres psql -U shopbot -d shopbot
```

## 7. Cập nhật phiên bản mới

```bash
cd /opt/telegram-shop-bot
git pull
docker compose -f docker-compose.prod.yml --profile tunnel up -d --build bot
```
Flyway tự chạy migration DB khi bot khởi động.

## 8. Thông báo hàng về vào group/channel

1. Thêm bot vào group/channel, cấp quyền **admin** (gửi tin nhắn).
2. Lấy chat id: group có dạng `-100xxxxxxxxxx`, channel công khai dùng `@ten_channel`.
3. Điền `NOTIFY_CHAT_ID` trong `.env`, restart bot.

## Xử lý sự cố

| Hiện tượng | Kiểm tra |
|---|---|
| Bot không trả lời | `logs bot` có `BOT_TOKEN chưa được cấu hình`? Token sai? |
| Chuyển khoản không tự cộng | SePay → lịch sử webhook: mã HTTP 401 → sai API key; không gửi được → tunnel/URL sai |
| Giao dịch "không khớp mã" | Admin nhận tin cảnh báo → dùng `/addbal user_id số_tiền` hoặc `/confirm` |
| Ảnh QR không hiện | VietQR lỗi tạm thời → bot tự gửi thông tin CK dạng text |
| Khách chuyển USDT nhưng bot chưa nhận | `logs bot \| grep USDT`: có `watcher ... error` → node lỗi, thử `CRYPTO_BSC_RPC` khác; khách chuyển sai mạng / sai số tiền → admin nhận cảnh báo kèm link tx |
