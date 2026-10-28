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

/**
 * 1 tài khoản TikTok người dùng gửi bán (chỉ email/SĐT + username, không có mật
 * khẩu).
 */
@Getter
@Setter
@Entity
@Table(name = "account_submissions")
public class AccountSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã hồ sơ, vd TK8K2P9QXA. */
    private String code;

    @Column(name = "user_id")
    private Long userId;

    /** Email hoặc số điện thoại đăng nhập của tài khoản. */
    private String contact;

    @Column(name = "account_password")
    private String accountPassword;

    /** Giá người bán tự đưa ra (VND). */
    @Column(name = "asking_price")
    private long askingPrice;

    @Enumerated(EnumType.STRING)
    private SubmissionStatus status;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** userId của admin đã duyệt/từ chối. */
    @Column(name = "decided_by")
    private Long decidedBy;
}
