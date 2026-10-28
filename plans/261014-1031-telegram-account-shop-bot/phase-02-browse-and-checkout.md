# Phase 02: Duyệt sản phẩm + Checkout

## Mục tiêu
Luồng mua hoàn chỉnh: ngôn ngữ → trang chủ → danh mục → SP → số lượng → thanh toán ví/QR → giao acc; tự hủy đơn sau 5 phút.

## Phụ thuộc
- Phase 01

## Files sẽ tạo
- `telegram/keyboards/`: `LanguageKeyboard`, `HomeMenuKeyboard`, `CatalogKeyboards`, `CheckoutKeyboards`
- `telegram/handlers/`: `StartLanguageHandler` (`/start`, payload `p_<id>`, `ref_<uid>`), `HomeInfoHandler` (Trang chủ, Hướng dẫn, Bảo hành, Hỗ trợ), `CatalogHandler`, `CheckoutHandler`, `PurchaseHistoryHandler`
- `service/`: `CatalogService`, `OrderService` (create, reserve, payByWallet, fulfill, cancel), `WalletService` (debit/credit + BalanceLog), `VietQrService`, `DeliveryService`, `PayRefGenerator`
- `scheduler/PaymentExpiryScheduler` (`@Scheduled(fixedDelay = 10s)`)

## UI chi tiết
- **Ngôn ngữ**: 🇻🇳 Tiếng Việt | 🇬🇧 English | 🇹🇭 ภาษาไทย | 🇨🇳 简体中文
- **Trang chủ**: Xem sản phẩm, Hướng dẫn, Lịch sử mua, Ví, Bảo hành, Hỗ trợ, Affiliate, Ngôn ngữ, Trang chủ
- **Danh mục**: hàng đầu = SP bán chạy (`✂️ CAPCUT PRO • 220.000đ • Đã bán 11`), "🗂️ DANH MỤC SẢN PHẨM", danh mục 2 cột `emoji Tên (số SP)`, cuối: 🔄 Làm mới | 🏠 Trang chủ
- **SP trong danh mục**: `Tên | 450.000đ | 📦 4` hoặc `🚫 Hết hàng`; ⬅️ Danh mục | 🔄 Làm mới | 🏠 Trang chủ
- **Chi tiết SP**: tên, 💳 Giá, 📦 Tồn kho, 📊 Đã bán, mô tả trong `<blockquote>`; ⬅️ Chọn sản phẩm khác; còn hàng → "✏️ Nhập số lượng cần mua (tối đa N):"
- **Validate số lượng**: số nguyên dương, ≤ tồn kho lúc nhập; sai → báo lỗi + hỏi lại; có nút Hủy
- **Ví của bạn**: số dư, tổng tiền → [💰 Thanh toán bằng ví] (nếu đủ) | [🏦 Chuyển khoản QR] | [❌ Hủy]
- **Tin QR** (sendPhoto URL `img.vietqr.io`): mã đơn, SP, SL, số tiền, hạn, thông tin CK; nút: 💬 Liên hệ admin | ❌ Hủy đơn ngay | 📜 Lịch sử mua | 💳 Ví | 🛍️ Sản phẩm | 🏠 Trang chủ

## Implementation steps
1. Keyboards + ngôn ngữ/trang chủ/info
2. Catalog
3. FSM số lượng (ConversationState)
4. `OrderService.reserve`: `@Transactional`, `FOR UPDATE SKIP LOCKED` lấy N stock AVAILABLE → RESERVED
5. Thanh toán ví: khóa row user (`PESSIMISTIC_WRITE`), trừ tiền, log, stock → SOLD, `sold += N`, giao acc
6. Đơn QR: pay_ref `DH` + 8 ký tự [A-Z0-9], gửi ảnh, lưu message_id
7. Hủy tay + scheduler hết hạn: release stock, `deleteMessage` tin QR (mất cả 2 phía), gửi "❌ Đơn hàng đã bị hủy"
8. Lịch sử mua (10 đơn gần nhất) + xem lại acc; > 20 acc gửi kèm file .txt

## Todo list
- [x] Ngôn ngữ + trang chủ + info
- [x] Catalog
- [x] FSM số lượng
- [x] Thanh toán ví
- [x] Thanh toán QR + hủy
- [x] Scheduler hết hạn
- [x] Lịch sử mua

## Definition of Done
- [x] Mua ví: số dư trừ đúng, nhận acc, stock SOLD, `sold` tăng
- [x] "abc", "0", "-1", "1.5", số > tồn → bị từ chối
- [x] Đơn QR quá 5 phút → hủy + acc về kho (test service). Xóa tin QR trên Telegram thật: _(chưa test trên bot thật — cần BOT_TOKEN)_
- [x] 2 user giành acc cuối → chỉ 1 người reserve được

## Risks
- deleteMessage lỗi (tin đã bị xóa / > 48h): catch, log, bỏ qua
- Restart giữa chừng: scheduler đọc DB nên vẫn hủy đúng
