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

/** Trạng thái hội thoại của 1 user (lưu DB để bot restart không mất). */
@Getter
@Setter
@Entity
@Table(name = "conversation_states")
public class ConversationState {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    private ConversationStep state;

    /** Dữ liệu kèm theo bước, vd "12" (productId) hoặc "12:3" (productId:quantity). */
    private String data;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
