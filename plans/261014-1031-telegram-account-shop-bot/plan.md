# Kế hoạch: Telegram Shop Bot bán tài khoản (Java Spring)

**Tạo lúc**: 2026-10-14T10:31 — cập nhật: đổi stack sang Java Spring
**Loại**: Sản phẩm thật

## Mục tiêu
Bot Telegram bán tài khoản/key tự động 24/7: đa ngôn ngữ, danh mục → sản phẩm → mua bằng ví hoặc QR (SePay webhook tự xác nhận), giao acc tự động, ví + lịch sử số dư, affiliate, thông báo hàng về.

## Phạm vi
**Trong scope**:
- Chọn ngôn ngữ (VI/EN/TH/ZH), trang chủ menu inline
- Danh mục 2 cột (kèm số SP) + SP bán chạy ở đầu + Làm mới / Trang chủ
- Chi tiết SP (giá, tồn, đã bán, mô tả), nhập số lượng có validate
- Thanh toán ví hoặc VietQR; giữ hàng 5 phút, hết hạn → hủy + xóa tin QR
- Ví: nạp tiền QR, lịch sử biến động số dư; lịch sử mua (xem lại acc)
- SePay webhook tự xác nhận đơn + nạp, chống trùng giao dịch
- Thông báo hàng về: group/channel + DM tất cả user, nút "Mua ngay"
- Affiliate: link giới thiệu + ghi nhận người được giới thiệu; **hoa hồng mặc định TẮT (0%)**, admin bật bằng `/setaff <percent>`
- Hướng dẫn / Bảo hành / Hỗ trợ: chỉ hiển thị nội dung
- Admin quản lý bằng lệnh Telegram
- Deploy Ubuntu VPS (6GB RAM) bằng Docker Compose + Cloudflare Tunnel cho webhook

**Ngoài scope**:
- Web admin panel, luồng xử lý yêu cầu bảo hành
- API Key đại lý (hoãn, làm sau khi bot chạy ổn)

## Ràng buộc
- Công nghệ: Java 17, Spring Boot 3.x, Maven, TelegramBots (long polling), Spring Data JPA, PostgreSQL 16, Flyway, Docker Compose
- Thanh toán: MB Bank + VietQR + SePay webhook
- **Webhook cần URL public cố định** → Cloudflare *named tunnel* cần domain trên Cloudflare (quick tunnel `trycloudflare.com` đổi URL mỗi lần restart, chỉ dùng để test)
- Class < 200 dòng, tách theo trách nhiệm

## Các phase

| # | Tên | Status | Ước tính | File |
|---|-----|--------|----------|------|
| 1 | Nền tảng: project, DB, i18n, khung bot | ✅ Hoàn thành | 3-4h | [phase-01](phase-01-spring-foundation-db-i18n.md) |
| 2 | Duyệt SP + checkout | ✅ Hoàn thành | 5-6h | [phase-02](phase-02-browse-and-checkout.md) |
| 3 | Ví, SePay webhook, affiliate, thông báo | ✅ Hoàn thành | 4-5h | [phase-03](phase-03-wallet-sepay-affiliate-notify.md) |
| 4 | Lệnh admin | ✅ Hoàn thành | 2-3h | [phase-04](phase-04-admin-commands.md) |
| 5 | Testing | 🟡 49 test tự động pass, chờ test tay trên bot thật | 3h | [phase-05](phase-05-testing.md) |
| 6 | Deploy VPS (Docker + Cloudflare Tunnel) | 🟡 Code + tài liệu xong, chờ deploy lên VPS | 2h | [phase-06](phase-06-docker-cloudflared-deployment.md) |

## Phụ thuộc
- Phase 2, 3, 4 phụ thuộc Phase 1; Phase 3 dùng OrderService của Phase 2
- Phase 5 sau 2-4; Phase 6 sau 5

## Risks
- Chưa có domain → webhook không có URL cố định: mua domain rẻ trỏ về Cloudflare; trong lúc chờ có `/confirm` xác nhận tay
- Bán trùng acc khi mua đồng thời: `SELECT ... FOR UPDATE SKIP LOCKED` khi reserve
- Chuyển tiền trễ/sai số tiền: phần không khớp luôn cộng vào ví
- Broadcast DM bị rate limit: gửi tuần tự ~20 msg/s, bỏ qua user đã block bot
