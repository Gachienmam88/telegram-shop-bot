# Phase 03: Ví, SePay webhook, Affiliate, Thông báo hàng về

## Mục tiêu
Nạp tiền QR, lịch sử số dư, SePay webhook tự xác nhận đơn/nạp, affiliate (hoa hồng tắt mặc định), thông báo hàng về group + DM.

## Phụ thuộc
- Phase 01, Phase 02 (OrderService, WalletService)

## Files sẽ tạo
- `telegram/handlers/WalletHandler` (màn Ví, FSM số tiền nạp, lịch sử biến động), `AffiliateHandler`
- `service/TopupService`, `PaymentMatcherService`, `AffiliateService`, `RestockNotifier`
- `web/SepayWebhookController` — `POST /sepay/webhook`
- `web/SepayWebhookPayload` (record)

## Chi tiết
- **Ví**: số dư; 💰 Nạp tiền | 📊 Lịch sử biến động số dư | 🏠 Trang chủ
- **Nạp tiền**: nhập số tiền (nguyên, TOPUP_MIN–TOPUP_MAX); tin QR giống ảnh mẫu (Mã nạp, Hạn thanh toán, Ngân hàng, Chủ TK, Số TK, Số tiền, Nội dung CK `NAP…`); nút 💳 Mở trang thanh toán | 💳 Xem ví | 🏠 Trang chủ; quá 5 phút xóa tin QR
- **Lịch sử số dư**: 15 dòng gần nhất `+100.000₫ · Nạp tiền NAPxxxx · 14/10 10:09 · Số dư: …`
- **SePay webhook** (đối chiếu doc SePay hiện tại khi code):
  - Header `Authorization: Apikey <SEPAY_API_KEY>` sai → 401
  - Chỉ xử lý `transferType == "in"`
  - Chống trùng theo `id` giao dịch (insert `bank_transactions`, trùng PK → bỏ qua)
  - Regex tìm `DH[A-Z0-9]{8}` / `NAP[A-Z0-9]{8}` trong `content`
  - Đơn PENDING + đủ tiền → fulfill, xóa tin QR, gửi acc; thừa → cộng ví
  - Thiếu tiền / đơn đã hủy / không tìm thấy user → cộng ví (nếu xác định được user) + thông báo; không xác định → log cho admin
  - Nạp → cộng đúng số tiền thực nhận (kể cả đã hết hạn)
  - Trả `{"success": true}` sau khi đã ghi nhận
- **Affiliate**:
  - Link `t.me/<BOT_USERNAME>?start=ref_<uid>`; chỉ gán referrer cho user mới, không tự giới thiệu
  - Màn Affiliate: link, số người đã giới thiệu, tổng hoa hồng, trạng thái
  - `app_settings.affiliate_percent` mặc định `0` → hiển thị "Hoa hồng: chưa kích hoạt", không cộng tiền
  - > 0 → mỗi đơn PAID của người được giới thiệu cộng `%` vào ví referrer (log `AFFILIATE`)
- **Thông báo hàng về** (theo ảnh mẫu): "📦 SẢN PHẨM VỪA ĐƯỢC THÊM HÀNG!", tên, ➕ Vừa thêm: n, 📦 Tồn kho hiện tại, 💰 Giá (~USDT), 📝 Mô tả; nút 🛒 Mua ngay (deep link `p_<id>`)
  - Gửi `NOTIFY_CHAT_ID` (tiếng Việt) + DM mọi user theo ngôn ngữ của họ
  - Chạy `@Async`, ~20 msg/s, bỏ qua lỗi 403 (user block bot)

## Todo list
- [x] Ví + nạp + hết hạn topup
- [x] Lịch sử biến động
- [x] Webhook controller + matcher + idempotency
- [x] Affiliate (+ setting bật/tắt)
- [x] Restock notifier

## Definition of Done
- [x] `curl` giả lập webhook → đơn được giao / ví được cộng
- [x] Gửi trùng webhook → chỉ xử lý 1 lần
- [x] Sai API key → 401
- [x] affiliate_percent = 0 → không cộng; = 5 → referrer nhận đúng 5%
- [ ] Nạp kho → group + user nhận thông báo; "Mua ngay" mở đúng SP _(chưa test trên bot thật — cần BOT_TOKEN)_

## Risks
- Ngân hàng chèn ký tự vào nội dung CK: regex, không so khớp cả chuỗi
- Webhook đến trước khi tin QR lưu message_id: lưu order trước, gửi tin sau, cập nhật message_id
