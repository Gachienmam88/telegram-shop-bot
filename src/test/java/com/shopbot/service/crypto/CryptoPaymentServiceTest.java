package com.shopbot.service.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shopbot.domain.CryptoNetwork;
import com.shopbot.service.CryptoInvoiceService;
import com.shopbot.service.OrderService;
import com.shopbot.service.PaymentOutcome;
import com.shopbot.support.PostgresTestBase;

/** Khớp giao dịch USDT với đơn / nạp: đúng số, lệch số, sai mạng, trùng, trễ. Tỷ giá test: 1 USDT = 27.000đ. */
class CryptoPaymentServiceTest extends PostgresTestBase {

    private static final long USER = 100;

    @Autowired
    CryptoCheckoutService checkout;

    @Autowired
    CryptoPaymentService payments;

    @Autowired
    OrderService orders;

    private int txCounter;

    /** Đơn 1 SP giá 100.000đ → ceil(100000 / 27000, 2) = 3.71 USDT (số tròn, không phần lẻ). */
    private CryptoCheckoutService.OrderInvoice newOrder(CryptoNetwork network) {
        return newOrder(network, 100_000);
    }

    private CryptoCheckoutService.OrderInvoice newOrder(CryptoNetwork network, long price) {
        if (jdbc.queryForObject("SELECT count(*) FROM users", Integer.class) == 0) {
            createUser(USER, 0);
        }
        long productId = createProduct(price, 2);
        return checkout.createOrder(USER, USER, productId, 1, network);
    }

    private PaymentOutcome send(CryptoNetwork network, BigDecimal amount) {
        return send(network, amount, "0xtx" + (++txCounter));
    }

    private PaymentOutcome send(CryptoNetwork network, BigDecimal amount, String txHash) {
        return payments.receive(new IncomingUsdtTransfer(network, txHash, 0, "0xsender", amount));
    }

    private String orderStatus(String code) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE code = ?", String.class, code);
    }

    @Test
    void invoiceAmountIsPriceRoundedUpToCents() {
        var a = newOrder(CryptoNetwork.BSC);
        var b = newOrder(CryptoNetwork.BSC, 120_000);

        assertThat(a.invoice().getUsdtAmount()).isEqualByComparingTo("3.71");
        assertThat(b.invoice().getUsdtAmount()).as("120000 / 27000 = 4.444 → làm tròn lên").isEqualByComparingTo("4.45");
        assertThat(a.order().getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofSeconds(1800)));
    }

    @Test
    void sameAmountOnSameNetworkIsBlockedUntilPaid() {
        var a = newOrder(CryptoNetwork.BSC);
        long productId = createProduct(100_000, 2);

        assertThatThrownBy(() -> checkout.createOrder(USER, USER, productId, 1, CryptoNetwork.BSC))
                .hasMessage(CryptoInvoiceService.AMOUNT_TAKEN);
        assertThat(stockCount(productId, "AVAILABLE")).as("đơn bị chặn không giữ hàng").isEqualTo(2);
        assertThat(checkout.createOrder(USER, USER, productId, 1, CryptoNetwork.TRON).invoice().getUsdtAmount())
                .as("mạng khác vẫn được").isEqualByComparingTo("3.71");

        send(CryptoNetwork.BSC, a.invoice().getUsdtAmount());
        assertThat(newOrder(CryptoNetwork.BSC).invoice().getUsdtAmount()).as("đã trả xong → số tiền được dùng lại")
                .isEqualByComparingTo("3.71");
    }

    @Test
    void exactAmountPaysOrderOnceEvenIfSeenTwice() {
        var created = newOrder(CryptoNetwork.TRON);
        BigDecimal amount = created.invoice().getUsdtAmount();

        PaymentOutcome out = send(CryptoNetwork.TRON, amount, "abc123");
        PaymentOutcome again = send(CryptoNetwork.TRON, amount, "abc123");

        assertThat(out).isInstanceOf(PaymentOutcome.OrderPaid.class);
        assertThat(((PaymentOutcome.OrderPaid) out).overpaid()).isZero();
        assertThat(((PaymentOutcome.OrderPaid) out).paid().accounts()).containsExactly("acc1|pass1");
        assertThat(again).isInstanceOf(PaymentOutcome.Duplicate.class);
        assertThat(orderStatus(created.order().getCode())).isEqualTo("PAID");
        assertThat(balanceOf(USER)).isZero();
    }

    @Test
    void sameAmountOnOtherNetworkDoesNotMatch() {
        var created = newOrder(CryptoNetwork.BSC);

        PaymentOutcome out = send(CryptoNetwork.POLYGON, created.invoice().getUsdtAmount());

        assertThat(out).isInstanceOf(PaymentOutcome.Unmatched.class);
        assertThat(orderStatus(created.order().getCode())).isEqualTo("PENDING");
    }

    @Test
    void underpaymentWithSingleOpenInvoiceCancelsAndCreditsWallet() {
        var created = newOrder(CryptoNetwork.BSC);

        // Sàn trừ phí: nhận 3.00 thay vì ~3.7x → 3.00 × 27000 = 81.000đ vào ví.
        PaymentOutcome out = send(CryptoNetwork.BSC, new BigDecimal("3.00"));

        assertThat(out).isInstanceOf(PaymentOutcome.OrderUnderpaid.class);
        assertThat(orderStatus(created.order().getCode())).isEqualTo("CANCELLED");
        assertThat(balanceOf(USER)).isEqualTo(81_000);
        assertThat(stockCount(created.order().getProductId(), "AVAILABLE")).isEqualTo(2);
    }

    @Test
    void overpaymentDeliversAndCreditsDifference() {
        var created = newOrder(CryptoNetwork.POLYGON);

        PaymentOutcome out = send(CryptoNetwork.POLYGON, created.invoice().getUsdtAmount().add(BigDecimal.ONE));

        assertThat(((PaymentOutcome.OrderPaid) out).overpaid()).isEqualTo(27_000);
        assertThat(balanceOf(USER)).isEqualTo(27_000);
    }

    @Test
    void wrongAmountIsUnmatchedWhenAmbiguousOrImplausible() {
        newOrder(CryptoNetwork.BSC);
        assertThat(send(CryptoNetwork.BSC, new BigDecimal("500"))).as("lệch quá xa").isInstanceOf(PaymentOutcome.Unmatched.class);

        newOrder(CryptoNetwork.BSC, 110_000);
        assertThat(send(CryptoNetwork.BSC, new BigDecimal("3.00"))).as("2 invoice đang mở").isInstanceOf(PaymentOutcome.Unmatched.class);
        assertThat(balanceOf(USER)).isZero();
    }

    @Test
    void paymentAfterCancelGoesToWallet() {
        var created = newOrder(CryptoNetwork.TRON);
        orders.cancelPending(created.order().getCode(), USER);
        mutableClock().advance(Duration.ofMinutes(35));

        PaymentOutcome out = send(CryptoNetwork.TRON, created.invoice().getUsdtAmount());

        assertThat(out).isInstanceOf(PaymentOutcome.LatePayment.class);
        assertThat(balanceOf(USER)).isEqualTo(100_000);
    }

    @Test
    void paymentAfterGraceWindowGoesToAdmin() {
        var created = newOrder(CryptoNetwork.TRON);
        // Hạn 30 phút + 10 phút ân hạn đã qua → không còn biết của ai.
        mutableClock().advance(Duration.ofMinutes(41));

        assertThat(send(CryptoNetwork.TRON, created.invoice().getUsdtAmount())).isInstanceOf(PaymentOutcome.Unmatched.class);
        assertThat(balanceOf(USER)).isZero();
    }

    @Test
    void topupCreditsConvertedAmount() {
        createUser(USER, 0);
        var created = checkout.createTopup(USER, USER, new BigDecimal("10"), CryptoNetwork.BSC);
        assertThat(created.topup().getAmount()).isEqualTo(270_000);
        assertThat(created.invoice().getUsdtAmount()).as("nạp 10 USDT → chuyển đúng 10").isEqualByComparingTo("10");

        PaymentOutcome out = send(CryptoNetwork.BSC, created.invoice().getUsdtAmount());

        assertThat(out).isInstanceOf(PaymentOutcome.TopupPaid.class);
        assertThat(balanceOf(USER)).isEqualTo(270_000);
    }
}
