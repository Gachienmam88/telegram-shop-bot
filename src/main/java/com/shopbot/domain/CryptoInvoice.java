package com.shopbot.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Yêu cầu chuyển USDT cho 1 đơn hàng / 1 lần nạp. Trên mỗi mạng, số tiền (usdtAmount) chỉ thuộc 1 invoice chưa
 * nhận tiền trong cửa sổ matchUntil → dùng để nhận diện giao dịch đến (USDT không có nội dung chuyển khoản).
 */
@Getter
@Setter
@Entity
@Table(name = "crypto_invoices")
public class CryptoInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private CryptoNetwork network;

    @Column(name = "usdt_amount")
    private BigDecimal usdtAmount;

    /** pay_ref của đơn (DH...) hoặc lệnh nạp (NAP...). */
    @Column(name = "pay_ref")
    private String payRef;

    /** Số VNĐ tương ứng khi khách chuyển đúng usdtAmount (giá đơn / số tiền nạp). */
    @Column(name = "vnd_amount")
    private long vndAmount;

    @Column(name = "created_at")
    private Instant createdAt;

    /** Hạn thanh toán (trùng hạn đơn/nạp). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Sau mốc này số tiền được dùng lại cho invoice khác; tiền đến trước mốc vẫn khớp (cộng ví nếu đơn đã hủy). */
    @Column(name = "match_until")
    private Instant matchUntil;

    /** Thời điểm nhận được giao dịch đầu tiên cho invoice này. */
    @Column(name = "matched_at")
    private Instant matchedAt;
}
