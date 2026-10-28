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

/** 1 dòng biến động số dư ví. */
@Getter
@Setter
@Entity
@Table(name = "balance_logs")
public class BalanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private long delta;

    @Column(name = "balance_after")
    private long balanceAfter;

    @Enumerated(EnumType.STRING)
    private BalanceReason reason;

    private String ref;

    @Column(name = "created_at")
    private Instant createdAt;
}
