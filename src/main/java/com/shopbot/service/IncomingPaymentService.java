package com.shopbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.shopbot.telegram.PaymentOutcomeNotifier;

/**
 * Điểm vào duy nhất cho tiền vào (SePay webhook, admin /confirm):
 * xử lý trong transaction (PaymentMatcherService) rồi mới gửi tin nhắn (sau khi đã commit).
 */
@Service
public class IncomingPaymentService {

    private static final Logger log = LoggerFactory.getLogger(IncomingPaymentService.class);

    private final PaymentMatcherService matcher;
    private final PaymentOutcomeNotifier notifier;

    public IncomingPaymentService(PaymentMatcherService matcher, PaymentOutcomeNotifier notifier) {
        this.matcher = matcher;
        this.notifier = notifier;
    }

    public PaymentOutcome receive(Long sepayId, long amount, String content) {
        PaymentOutcome outcome;
        try {
            outcome = matcher.process(sepayId, amount, content);
        } catch (DataIntegrityViolationException e) {
            // 2 webhook trùng id chạy song song: request sau vấp khóa chính → coi là trùng.
            log.info("Duplicate SePay transaction {}", sepayId);
            return new PaymentOutcome.Duplicate();
        }
        log.info("Payment sepayId={} amount={} -> {}", sepayId, amount, outcome.getClass().getSimpleName());
        try {
            notifier.notify(outcome);
        } catch (Exception e) {
            log.error("Failed to notify payment outcome", e);
        }
        return outcome;
    }
}
