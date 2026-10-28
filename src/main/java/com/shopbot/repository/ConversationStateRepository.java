package com.shopbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopbot.domain.ConversationState;

public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {
}
