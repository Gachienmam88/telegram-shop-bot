# Phase 04: Lệnh Admin

## Mục tiêu
Admin (theo `ADMIN_IDS`) quản lý danh mục, sản phẩm, kho, thanh toán, affiliate trong Telegram.

## Phụ thuộc
- Phase 01; RestockNotifier, PaymentMatcherService (Phase 03)

## Files sẽ tạo
- `telegram/admin/AdminGuard`
- `telegram/admin/AdminCatalogCommands`
- `telegram/admin/AdminStockCommands` (text + file .txt qua getFile)
- `telegram/admin/AdminPaymentCommands`
- `service/StatsService`

## Lệnh
| Lệnh | Tác dụng |
|---|---|
| `/admin` | Danh sách lệnh |
| `/addcat 🎬 \| CapCut` · `/cats` | Thêm / liệt kê danh mục |
| `/addproduct <cat_id> \| ✂️ \| Tên \| 220000` | Thêm SP |
| `/products [cat_id]` | Liệt kê SP (id, giá, tồn) |
| `/setdesc <id>` + xuống dòng mô tả | Sửa mô tả (HTML) |
| `/setprice <id> <giá>` · `/setname <id> <tên>` | Sửa giá / tên |
| `/hide <id>` · `/show <id>` | Ẩn / hiện SP |
| `/addstock <id>` + mỗi dòng 1 acc, hoặc file .txt caption `/addstock <id>` | Nạp kho → tự thông báo hàng về |
| `/stock <id>` | Xem tồn kho |
| `/confirm <DHxxxx\|NAPxxxx>` | Xác nhận thanh toán tay |
| `/addbal <user_id> <số tiền>` | Cộng/trừ ví (âm = trừ) |
| `/setaff <percent>` | Bật/tắt hoa hồng affiliate (0 = tắt) |
| `/stats` | Doanh thu hôm nay/tổng, số đơn, số user |

## Implementation steps
1. AdminGuard (user không phải admin → bỏ qua im lặng)
2. CRUD danh mục/SP
3. Nạp kho: bỏ dòng trống, trùng trong batch, trùng với kho hiện có → gọi RestockNotifier
4. `/confirm` dùng chung PaymentMatcherService
5. `/addbal`, `/setaff`, `/stats`

## Todo list
- [x] Guard
- [x] Danh mục/SP
- [x] Nạp kho
- [x] /confirm, /addbal, /setaff
- [x] /stats

## Definition of Done
- [x] User thường gõ lệnh admin → không chạy lệnh (bot hiện trang chủ)
- [ ] Nạp 100 acc từ file .txt → tồn +100, thông báo được gửi _(chưa test trên bot thật — cần BOT_TOKEN)_
- [x] Sai cú pháp → trả về hướng dẫn cú pháp đúng

## Risks
- File .txt lớn: giới hạn 5MB / 10.000 dòng mỗi lần
