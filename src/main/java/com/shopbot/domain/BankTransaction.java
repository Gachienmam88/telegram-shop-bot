package com.shopbot.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Giao dịch SePay đã nhận (khóa chính = id giao dịch SePay) để chống xử lý trùng. */
@Getter
@Setter
@Entity
@Table(name = "bank_transactions")
public class BankTransaction {

    @Id
    @Column(name = "sepay_id")
    private Long sepayId;

    private long amount;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "matched_ref")
    private String matchedRef;

    @Column(name = "created_at")
    private Instant createdAt;
}
