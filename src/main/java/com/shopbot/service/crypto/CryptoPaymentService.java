package com.shopbot.service.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.shopbot.service.PaymentOutcome;
import com.shopbot.telegram.PaymentOutcomeNotifier;

/** Điểm vào cho giao dịch USDT: xử lý trong transaction rồi mới báo khách/admin (sau commit). */
@Service
public class CryptoPaymentService {

    private static final Logger log = LoggerFactory.getLogger(CryptoPaymentService.class);

    private final CryptoPaymentMatcher matcher;
    private final PaymentOutcomeNotifier notifier;

    public CryptoPaymentService(CryptoPaymentMatcher matcher, PaymentOutcomeNotifier notifier) {
        this.matcher = matcher;
        this.notifier = notifier;
    }

    public PaymentOutcome receive(IncomingUsdtTransfer transfer) {
        PaymentOutcome outcome;
        try {
            outcome = matcher.process(transfer);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate USDT transfer {}", transfer.key());
            return new PaymentOutcome.Duplicate();
        }
        log.info("USDT {} {} tx={} -> {}", transfer.network(), transfer.amount().toPlainString(), transfer.txHash(),
                outcome.getClass().getSimpleName());
        try {
            notifier.notify(outcome);
        } catch (Exception e) {
            log.error("Failed to notify crypto payment outcome", e);
        }
        return outcome;
    }
}
