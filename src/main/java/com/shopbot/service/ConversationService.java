package com.shopbot.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.ConversationState;
import com.shopbot.domain.ConversationStep;
import com.shopbot.repository.ConversationStateRepository;

/** Lưu bước hội thoại đang chờ nhập (số lượng, số tiền nạp, xác nhận checkout). */
@Service
public class ConversationService {

    /** Trạng thái cũ hơn mức này coi như hết hạn. */
    private static final Duration TTL = Duration.ofMinutes(30);

    private final ConversationStateRepository repo;
    private final Clock clock;

    public ConversationService(ConversationStateRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<ConversationState> current(long userId) {
        return repo.findById(userId)
                .filter(s -> s.getUpdatedAt().plus(TTL).isAfter(clock.instant()));
    }

    @Transactional
    public void set(long userId, ConversationStep step, String data) {
        ConversationState s = repo.findById(userId).orElseGet(ConversationState::new);
        s.setUserId(userId);
        s.setState(step);
        s.setData(data);
        s.setUpdatedAt(clock.instant());
        repo.save(s);
    }

    @Transactional
    public void clear(long userId) {
        repo.findById(userId).ifPresent(repo::delete);
    }

    /** Lấy và xóa luôn trạng thái nếu đúng bước (chống bấm nút 2 lần). */
    @Transactional
    public Optional<String> consume(long userId, ConversationStep expected) {
        Optional<ConversationState> s = current(userId).filter(st -> st.getState() == expected);
        repo.findById(userId).ifPresent(repo::delete);
        return s.map(ConversationState::getData);
    }
}
