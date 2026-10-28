package com.shopbot.web;

/**
 * Body webhook SePay gửi khi tài khoản ngân hàng có biến động.
 * Xem mục Webhooks tại docs.sepay.vn để đối chiếu khi SePay thay đổi định dạng.
 */
public record SepayWebhookPayload(
        Long id,
        String gateway,
        String transactionDate,
        String accountNumber,
        String code,
        String content,
        String transferType,
        Long transferAmount,
        Long accumulated,
        String subAccount,
        String referenceCode,
        String description) {
}
