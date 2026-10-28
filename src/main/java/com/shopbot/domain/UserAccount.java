package com.shopbot.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private String username;

    private String lang = "vi";

    /** Số dư ví (VND). */
    private long balance;

    @Column(name = "referrer_id")
    private Long referrerId;

    /** Thông tin nhận tiền khi rút (nhập 1 lần, dùng lại). */
    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_account_holder")
    private String bankAccountHolder;

    @Column(name = "created_at")
    private Instant createdAt;

    /** Đã nhập đủ thông tin nhận tiền chưa. */
    public boolean hasBankInfo() {
        return bankName != null && bankAccountNumber != null && bankAccountHolder != null;
    }
}
