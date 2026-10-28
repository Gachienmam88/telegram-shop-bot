package com.shopbot.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.BalanceReason;
import com.shopbot.domain.BankTransaction;
import com.shopbot.domain.OrderStatus;
import com.shopbot.domain.ShopOrder;
import com.shopbot.domain.Topup;
import com.shopbot.domain.TopupStatus;
import com.shopbot.repository.BankTransactionRepository;
import com.shopbot.repository.OrderRepository;
import com.shopbot.repository.TopupRepository;
import com.shopbot.util.CodeGenerator;
import com.shopbot.util.PayRefExtractor;

/**
 * Khớp 1 giao dịch tiền vào với đơn hàng / yêu cầu nạp theo mã trong nội dung CK.
 * Nguyên tắc: tiền khách đã chuyển không bao giờ mất — không khớp đơn thì cộng vào ví.
 */
@Service
public class PaymentMatcherService {

    private final BankTransactionRepository bankTransactions;
    private final OrderRepository orders;
    private final TopupRepository topups;
    private final OrderService orderService;
    private final WalletService wallet;
    private final Clock clock;

    public PaymentMatcherService(BankTransactionRepository bankTransactions, OrderRepository orders,
                                 TopupRepository topups, OrderService orderService, WalletService wallet,
                                 Clock clock) {
        this.bankTransactions = bankTransactions;
        this.orders = orders;
        this.topups = topups;
        this.orderService = orderService;
        this.wallet = wallet;
        this.clock = clock;
    }

    /**
     * @param sepayId id giao dịch SePay (null khi admin xác nhận tay)
     */
    @Transactional
    public PaymentOutcome process(Long sepayId, long amount, String content) {
        if (sepayId != null && bankTransactions.existsById(sepayId)) {
            return new PaymentOutcome.Duplicate();
        }
        Optional<String> ref = PayRefExtractor.find(content);
        if (sepayId != null) {
            recordTransaction(sepayId, amount, content, ref.orElse(null));
        }
        if (ref.isEmpty() || amount <= 0) {
            return new PaymentOutcome.Unmatched(amount, content);
        }
        return applyToRef(ref.get(), amount, content);
    }

    /**
     * Áp số tiền (VNĐ) đã nhận vào đơn / lệnh nạp có mã ref: đủ → giao hàng, thiếu/dư/trễ → cộng ví.
     * Dùng chung cho chuyển khoản ngân hàng và USDT (đã quy đổi VNĐ). Gọi trong transaction của caller.
     */
    @Transactional
    public PaymentOutcome applyToRef(String ref, long amount, String content) {
        return ref.startsWith(CodeGenerator.TOPUP_REF_PREFIX) ? handleTopup(ref, amount, content) : handleOrder(ref, amount, content);
    }

    private PaymentOutcome handleOrder(String ref, long amount, String content) {
        Optional<ShopOrder> found = orders.findByPayRefForUpdate(ref);
        if (found.isEmpty()) {
            return new PaymentOutcome.Unmatched(amount, content);
        }
        ShopOrder o = found.get();
        if (o.getStatus() == OrderStatus.PENDING && amount >= o.getAmount()) {
            PaidOrder paid = orderService.fulfillReserved(o);
            long extra = amount - o.getAmount();
            if (extra > 0) {
                wallet.credit(o.getUserId(), extra, BalanceReason.BANK_CREDIT, o.getCode());
            }
            return new PaymentOutcome.OrderPaid(paid, extra);
        }
        if (o.getStatus() == OrderStatus.PENDING) {
            orderService.cancelLocked(o);
            long balance = wallet.credit(o.getUserId(), amount, BalanceReason.BANK_CREDIT, o.getCode());
            return new PaymentOutcome.OrderUnderpaid(o, amount, balance);
        }
        long balance = wallet.credit(o.getUserId(), amount, BalanceReason.BANK_CREDIT, o.getCode());
        return new PaymentOutcome.LatePayment(o, amount, balance);
    }

    private PaymentOutcome handleTopup(String ref, long amount, String content) {
        Optional<Topup> found = topups.findByPayRefForUpdate(ref);
        if (found.isEmpty()) {
            return new PaymentOutcome.Unmatched(amount, content);
        }
        Topup t = found.get();
        boolean wasPending = t.getStatus() == TopupStatus.PENDING;
        if (t.getStatus() != TopupStatus.PAID) {
            t.setStatus(TopupStatus.PAID);
            t.setPaidAmount(amount);
            t.setPaidAt(clock.instant());
        }
        // Cộng đúng số tiền thực nhận (kể cả chuyển khác số yêu cầu hoặc chuyển 2 lần).
        long balance = wallet.credit(t.getUserId(), amount, BalanceReason.TOPUP, t.getPayRef());
        return new PaymentOutcome.TopupPaid(t, amount, balance, wasPending);
    }

    private void recordTransaction(long sepayId, long amount, String content, String ref) {
        BankTransaction tx = new BankTransaction();
        tx.setSepayId(sepayId);
        tx.setAmount(amount);
        tx.setContent(content);
        tx.setMatchedRef(ref);
        tx.setCreatedAt(clock.instant());
        // saveAndFlush: nếu 2 webhook trùng chạy song song, request sau lỗi khóa chính ngay tại đây.
        bankTransactions.saveAndFlush(tx);
    }
}
