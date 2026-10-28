# Phase 05: Testing

## Mục tiêu
Test tự động cho logic tiền + kho trên PostgreSQL thật (Testcontainers), test tay luồng UI.

## Phụ thuộc
- Phase 02, 03, 04

## Files sẽ tạo
- `src/test/java/.../support/PostgresTestBase` (Testcontainers)
- `OrderServiceTest`, `WalletServiceTest`, `PaymentMatcherServiceTest`, `QuantityValidatorTest`, `AffiliateServiceTest`
- `SepayWebhookControllerTest` (MockMvc)

## Test cases chính
- Reserve vượt tồn → lỗi; 2 thread giành acc cuối → 1 thành công
- Thanh toán ví đủ/thiếu; số dư + BalanceLog đúng
- Hết hạn → release stock, CANCELLED
- Webhook: đúng tiền, thừa, thiếu, đơn đã hủy, trùng id, sai API key, transferType out
- Validate số lượng: "abc", "0", "-1", "1.5", " 3 ", > tồn
- Affiliate: 0% không cộng, 5% đúng tiền, không tự giới thiệu, chỉ gán cho user mới

## Todo list
- [x] Testcontainers base
- [x] Các test trên
- [ ] Checklist test tay (4 ngôn ngữ, xóa tin QR, deep link, nạp tiền)

## Definition of Done
- [x] `mvn test` pass 100%
- [ ] Checklist test tay xong trên bot thật

## Risks
- Testcontainers cần Docker trên máy dev: đã có Docker Desktop thì OK, nếu không chạy test trên VPS
- Test phụ thuộc thời gian: inject `Clock` vào service
