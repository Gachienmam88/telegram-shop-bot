package com.shopbot.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class ShopOrder {

    /** Mã đơn hiển thị cho khách, vd ORD-20261014103000-531. */
    @Id
    private String code;

    /** Mã nội dung chuyển khoản, vd DH8K2P9QXA. */
    @Column(name = "pay_ref")
    private String payRef;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "product_id")
    private Long productId;

    /** Tên SP tại thời điểm mua (giữ nguyên nếu admin đổi tên sau này). */
    @Column(name = "product_name")
    private String productName;

    private int quantity;

    @Column(name = "unit_price")
    private long unitPrice;

    private long amount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method")
    private PayMethod payMethod;

    @Column(name = "chat_id")
    private Long chatId;

    /** Tin nhắn chứa QR thanh toán (để xóa khi hủy / đã thanh toán). */
    @Column(name = "payment_message_id")
    private Integer paymentMessageId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
