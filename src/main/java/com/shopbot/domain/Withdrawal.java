package com.shopbot.domain;

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

/** 1 yêu cầu rút tiền từ ví. Thông tin ngân hàng chụp lại tại thời điểm tạo. */
@Getter
@Setter
@Entity
@Table(name = "withdrawals")
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã rút, vd RUT8K2P9QXA. */
    private String code;

    @Column(name = "user_id")
    private Long userId;

    private long amount;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_account_holder")
    private String bankAccountHolder;

    @Enumerated(EnumType.STRING)
    private WithdrawalStatus status;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** userId của admin đã bấm đã chuyển / từ chối. */
    @Column(name = "decided_by")
    private Long decidedBy;
}
