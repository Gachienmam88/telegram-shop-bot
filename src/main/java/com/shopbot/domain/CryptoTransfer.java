package com.shopbot.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Giao dịch USDT đến đã xử lý (khóa chính = mạng:tx:logIndex) để không cộng tiền 2 lần. */
@Getter
@Setter
@Entity
@Table(name = "crypto_transfers")
public class CryptoTransfer {

    @Id
    @Column(name = "tx_key")
    private String txKey;

    @Enumerated(EnumType.STRING)
    private CryptoNetwork network;

    @Column(name = "tx_hash")
    private String txHash;

    private BigDecimal amount;

    @Column(name = "matched_ref")
    private String matchedRef;

    @Column(name = "created_at")
    private Instant createdAt;
}
