package com.shopbot.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id")
    private Long categoryId;

    private String emoji = "";

    private String name;

    /** Đơn giá (VND). */
    private long price;

    /** Mô tả dạng HTML Telegram (admin nhập qua /addproduct hoặc /setdesc). */
    @Column(columnDefinition = "text")
    private String description = "";

    private int sold;

    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    public String label() {
        return emoji.isBlank() ? name : emoji + " " + name;
    }
}
