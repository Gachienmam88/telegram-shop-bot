package com.shopbot.service.crypto;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.CryptoInvoice;
import com.shopbot.domain.CryptoTransfer;
import com.shopbot.repository.CryptoInvoiceRepository;
import com.shopbot.repository.CryptoTransferRepository;
import com.shopbot.service.CryptoInvoiceService;
import com.shopbot.service.PaymentMatcherService;
import com.shopbot.service.PaymentOutcome;

/**
 * Khớp 1 giao dịch USDT đến với invoice:
 * 1. Đúng số tiền → invoice đó.
 * 2. Lệch số tiền (vd sàn trừ phí) → chỉ khi có đúng 1 invoice đang mở cùng mạng và số nhận trong 50%–150%.
 * 3. Còn lại → báo admin.
 * Sau đó quy đổi VNĐ và dùng chung logic đủ/thiếu/dư/trễ của PaymentMatcherService.
 */
@Service
public class CryptoPaymentMatcher {

    private static final BigDecimal MIN_RATIO = new BigDecimal("0.5");
    private static final BigDecimal MAX_RATIO = new BigDecimal("1.5");

    private final CryptoTransferRepository transfers;
    private final CryptoInvoiceRepository invoices;
    private final CryptoInvoiceService invoiceService;
    private final PaymentMatcherService matcher;
    private final Clock clock;

    public CryptoPaymentMatcher(CryptoTransferRepository transfers, CryptoInvoiceRepository invoices,
                                CryptoInvoiceService invoiceService, PaymentMatcherService matcher, Clock clock) {
        this.transfers = transfers;
        this.invoices = invoices;
        this.invoiceService = invoiceService;
        this.matcher = matcher;
        this.clock = clock;
    }

    @Transactional
    public PaymentOutcome process(IncomingUsdtTransfer t) {
        if (transfers.existsById(t.key())) {
            return new PaymentOutcome.Duplicate();
        }
        Instant now = clock.instant();
        Optional<CryptoInvoice> invoice = findInvoice(t, now);
        record(t, invoice.map(CryptoInvoice::getPayRef).orElse(null), now);
        String content = describe(t);
        if (invoice.isEmpty() || t.amount().signum() <= 0) {
            long vnd = t.amount().signum() > 0 ? invoiceService.toVnd(t.amount()) : 0;
            return new PaymentOutcome.Unmatched(vnd, content);
        }
        CryptoInvoice i = invoice.get();
        if (i.getMatchedAt() == null) {
            i.setMatchedAt(now);
        }
        long vnd = t.amount().compareTo(i.getUsdtAmount()) >= 0
                ? i.getVndAmount() + invoiceService.toVnd(t.amount().subtract(i.getUsdtAmount()))
                : invoiceService.toVnd(t.amount());
        if (vnd <= 0) {
            return new PaymentOutcome.Unmatched(0, content);
        }
        return matcher.applyToRef(i.getPayRef(), vnd, content);
    }

    private Optional<CryptoInvoice> findInvoice(IncomingUsdtTransfer t, Instant now) {
        List<CryptoInvoice> exact = invoices.findMatching(t.network(), t.amount(), now);
        if (!exact.isEmpty()) {
            return Optional.of(exact.get(0));
        }
        List<CryptoInvoice> open = invoices.findOpen(t.network(), now);
        if (open.size() != 1) {
            return Optional.empty();
        }
        CryptoInvoice only = open.get(0);
        BigDecimal ratio = t.amount().divide(only.getUsdtAmount(), 4, java.math.RoundingMode.HALF_UP);
        boolean plausible = ratio.compareTo(MIN_RATIO) >= 0 && ratio.compareTo(MAX_RATIO) <= 0;
        return plausible ? Optional.of(only) : Optional.empty();
    }

    private void record(IncomingUsdtTransfer t, String ref, Instant now) {
        CryptoTransfer ct = new CryptoTransfer();
        ct.setTxKey(t.key());
        ct.setNetwork(t.network());
        ct.setTxHash(t.txHash());
        ct.setAmount(t.amount());
        ct.setMatchedRef(ref);
        ct.setCreatedAt(now);
        // saveAndFlush: 2 lần xử lý song song cùng tx → lần sau lỗi khóa chính ngay tại đây.
        transfers.saveAndFlush(ct);
    }

    /** Nội dung ghi lại cho admin: số USDT, mạng, link giao dịch. */
    static String describe(IncomingUsdtTransfer t) {
        return t.amount().stripTrailingZeros().toPlainString() + " USDT · " + t.network().displayName()
                + " · " + t.network().explorerTxUrl(t.txHash());
    }
}
