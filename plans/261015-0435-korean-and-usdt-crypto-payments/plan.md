# Kế hoạch: Tiếng Hàn + thanh toán USDT tự động (BSC / TRON / Polygon)

**Tạo lúc**: 2026-10-15 04:35 (Asia/Saigon) · **Loại**: Sản phẩm thật

## Mục tiêu
Thêm ngôn ngữ ko; khách mua hàng + nạp ví bằng USDT, bot tự nhận tiền khi giao dịch on-chain đủ xác nhận.

## Quyết định đã chốt (với user)
- USDT cho cả mua hàng + nạp ví. Tỷ giá cố định `USDT_RATE`.
- Thiếu/dư tiền → quy đổi VNĐ vào ví (thiếu: hủy đơn + cộng ví; dư: giao hàng + cộng phần dư).
- Ấn Độ dùng tiếng Anh sẵn có → chỉ thêm tiếng Hàn.

## Thiết kế
- **Nhận diện đơn = số tiền tròn** (cập nhật 2026-10-15): `ceil(VNĐ / rate, 2)`, nạp X USDT → đúng X; khách tự trả phí mạng. 1 số tiền / mạng chỉ giữ cho 1 invoice chưa trả đến `expiresAt + 10 phút`; trùng → khách chọn mạng khác.
- **Không cần private key**: chỉ đọc giao dịch đến 3 địa chỉ.
  - BSC/Polygon: JSON-RPC `eth_getLogs` (Transfer → địa chỉ shop) trên node publicnode, lọc đúng contract USDT, chờ N block.
  - TRON: TronGrid `/v1/accounts/{addr}/transactions/trc20?only_confirmed=true&contract_address=USDT`.
  - Poll 15s, con trỏ block/timestamp lưu `app_settings`, chống trùng bằng bảng `crypto_transfers` (PK tx).
- **Khớp**: đúng số tiền → invoice; không đúng → nếu chỉ có 1 invoice đang mở cùng mạng và số nhận trong [50%,150%] → gán cho invoice đó; còn lại → báo admin.
- Tái dùng `PaymentMatcherService` (logic đủ/thiếu/dư/trễ) với số VNĐ quy đổi.
- Đơn USDT giữ hàng `CRYPTO_TIMEOUT_SECONDS` (mặc định 1800s).

## Contract USDT (đã kiểm tra on-chain 2026-10-15)
| Mạng | Contract | Decimals | Xác nhận |
|---|---|---|---|
| BSC | 0x55d398326f99059fF775485246999027B3197955 | 18 | 15 block |
| Polygon | 0xc2132D05D31c914a87C6611C10748AEb04B58e8F (USDT0) | 6 | 30 block |
| TRON | TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t | 6 | only_confirmed |

## Phases
| # | Tên | Status |
|---|-----|--------|
| 1 | i18n tiếng Hàn + key mới | ✅ |
| 2 | DB V3 + invoice + matcher | ✅ |
| 3 | Watchers BSC/Polygon/TRON + scheduler | ✅ |
| 4 | UI Telegram (checkout + nạp USDT) | ✅ |
| 5 | Test + docs | ✅ |

## Risks
- Sàn trừ phí rút → khách nhận thiếu → fallback khớp 1 invoice / báo admin.
- Node công khai lỗi/giới hạn → retry tick sau, con trỏ không tiến.
- Chuyển tiền cá nhân vào ví shop khi đang có invoice mở có thể bị gán nhầm (giới hạn 50–150%) → khuyến nghị không dùng ví này cho việc khác.
