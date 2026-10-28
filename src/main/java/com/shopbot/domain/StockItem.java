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

/** 1 tài khoản/key trong kho. */
@Getter
@Setter
@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    private StockStatus status;

    /** Đơn đang giữ / đã mua tài khoản này. */
    @Column(name = "order_code")
    private String orderCode;

    @Column(name = "created_at")
    private Instant createdAt;

    /** Thời điểm xóa mềm (null = chưa xóa). */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
