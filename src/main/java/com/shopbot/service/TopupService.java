package com.shopbot.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.Topup;
import com.shopbot.domain.TopupStatus;
import com.shopbot.repository.TopupRepository;
import com.shopbot.util.CodeGenerator;

/** Yêu cầu nạp tiền vào ví qua chuyển khoản QR. */
@Service
public class TopupService {

    private final TopupRepository topups;
    private final CodeGenerator codes;
    private final ShopProperties props;
    private final Clock clock;

    public TopupService(TopupRepository topups, CodeGenerator codes, ShopProperties props, Clock clock) {
        this.topups = topups;
        this.codes = codes;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Topup create(long userId, long chatId, long amount) {
        return create(userId, chatId, amount, props.payment().timeoutSeconds());
    }

    /** Lệnh nạp amount VNĐ, hết hạn sau timeoutSeconds (USDT cần lâu hơn chuyển khoản). */
    @Transactional
    public Topup create(long userId, long chatId, long amount, int timeoutSeconds) {
        Topup t = new Topup();
        String code;
        do {
            code = codes.topupCode();
        } while (topups.existsById(code));
        t.setCode(code);
        t.setPayRef(codes.payRef(CodeGenerator.TOPUP_REF_PREFIX));
        t.setUserId(userId);
        t.setChatId(chatId);
        t.setAmount(amount);
        t.setStatus(TopupStatus.PENDING);
        t.setCreatedAt(clock.instant());
        t.setExpiresAt(clock.instant().plus(Duration.ofSeconds(timeoutSeconds)));
        return topups.save(t);
    }

    /** Lưu id tin QR. Trả false nếu đã không còn PENDING (caller nên xóa tin QR). */
    @Transactional
    public boolean attachPaymentMessage(String code, int messageId) {
        Topup t = topups.findForUpdate(code).orElseThrow();
        t.setPaymentMessageId(messageId);
        return t.getStatus() == TopupStatus.PENDING;
    }

    /** PENDING quá hạn → EXPIRED. Tiền chuyển muộn vẫn được cộng (xem PaymentMatcherService). */
    @Transactional
    public Optional<Topup> expire(String code) {
        return topups.findForUpdate(code)
                .filter(t -> t.getStatus() == TopupStatus.PENDING)
                .map(t -> {
                    t.setStatus(TopupStatus.EXPIRED);
                    return t;
                });
    }

    @Transactional(readOnly = true)
    public List<String> expiredPendingCodes() {
        return topups.findCodesByStatusAndExpiresAtBefore(TopupStatus.PENDING, clock.instant());
    }
}
