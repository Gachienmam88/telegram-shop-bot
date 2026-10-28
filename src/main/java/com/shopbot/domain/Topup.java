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

/** Yêu cầu nạp tiền vào ví. */
@Getter
@Setter
@Entity
@Table(name = "topups")
public class Topup {

    @Id
    private String code;

    /** Mã nội dung chuyển khoản, vd NAP8K2P9QXA. */
    @Column(name = "pay_ref")
    private String payRef;

    @Column(name = "user_id")
    private Long userId;

    private long amount;

    @Column(name = "paid_amount")
    private Long paidAmount;

    @Enumerated(EnumType.STRING)
    private TopupStatus status;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "payment_message_id")
    private Integer paymentMessageId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
