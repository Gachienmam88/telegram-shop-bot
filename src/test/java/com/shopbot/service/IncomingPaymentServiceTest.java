package com.shopbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shopbot.domain.ShopOrder;
import com.shopbot.domain.Topup;
import com.shopbot.support.PostgresTestBase;

/** Khớp tiền vào (SePay) với đơn / nạp: đủ, thừa, thiếu, trễ, trùng, không khớp. */
class IncomingPaymentServiceTest extends PostgresTestBase {

    @Autowired
    IncomingPaymentService payments;

    @Autowired
    OrderService orders;

    @Autowired
    TopupService topups;

    private ShopOrder pendingOrder(int stock, int quantity) {
        createUser(100, 0);
        long productId = createProduct(100_000, stock);
        return orders.createBankOrder(100, 100, productId, quantity);
    }

    @Test
    void exactAmountPaysOrderAndDeliversAccounts() {
        ShopOrder o = pendingOrder(3, 2);

        PaymentOutcome out = payments.receive(1L, 200_000, "MB 123 " + o.getPayRef() + " chuyen tien");

        assertThat(out).isInstanceOf(PaymentOutcome.OrderPaid.class);
        assertThat(((PaymentOutcome.OrderPaid) out).paid().accounts()).hasSize(2);
        assertThat(stockCount(o.getProductId(), "SOLD")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE code = ?", String.class, o.getCode()))
                .isEqualTo("PAID");
        assertThat(balanceOf(100)).isZero();
    }

    @Test
    void overpaymentGoesToWallet() {
        ShopOrder o = pendingOrder(1, 1);

        PaymentOutcome out = payments.receive(1L, 150_000, o.getPayRef());

        assertThat(((PaymentOutcome.OrderPaid) out).overpaid()).isEqualTo(50_000);
        assertThat(balanceOf(100)).isEqualTo(50_000);
    }

    @Test
    void underpaymentCancelsOrderAndCreditsWallet() {
        ShopOrder o = pendingOrder(2, 2);

        PaymentOutcome out = payments.receive(1L, 150_000, o.getPayRef());

        assertThat(out).isInstanceOf(PaymentOutcome.OrderUnderpaid.class);
        assertThat(balanceOf(100)).isEqualTo(150_000);
        assertThat(stockCount(o.getProductId(), "AVAILABLE")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE code = ?", String.class, o.getCode()))
                .isEqualTo("CANCELLED");
    }

    @Test
    void paymentAfterCancellationIsCreditedToWallet() {
        ShopOrder o = pendingOrder(1, 1);
        orders.cancelPending(o.getCode(), null);

        PaymentOutcome out = payments.receive(1L, 100_000, o.getPayRef());

        assertThat(out).isInstanceOf(PaymentOutcome.LatePayment.class);
        assertThat(balanceOf(100)).isEqualTo(100_000);
        assertThat(stockCount(o.getProductId(), "AVAILABLE")).isEqualTo(1);
    }

    @Test
    void duplicateWebhookIsProcessedOnce() {
        createUser(100, 0);
        Topup t = topups.create(100, 100, 100_000);

        payments.receive(77L, 100_000, t.getPayRef());
        PaymentOutcome second = payments.receive(77L, 100_000, t.getPayRef());

        assertThat(second).isInstanceOf(PaymentOutcome.Duplicate.class);
        assertThat(balanceOf(100)).isEqualTo(100_000);
    }

    @Test
    void topupCreditsActualAmountEvenAfterExpiry() {
        createUser(100, 0);
        Topup t = topups.create(100, 100, 100_000);
        mutableClock().advance(Duration.ofMinutes(10));
        topups.expire(t.getCode());

        PaymentOutcome out = payments.receive(5L, 120_000, "nap tien " + t.getPayRef().toLowerCase());

        assertThat(out).isInstanceOf(PaymentOutcome.TopupPaid.class);
        assertThat(((PaymentOutcome.TopupPaid) out).wasPending()).isFalse();
        assertThat(balanceOf(100)).isEqualTo(120_000);
        assertThat(jdbc.queryForObject("SELECT reason FROM balance_logs WHERE user_id = 100", String.class))
                .isEqualTo("TOPUP");
    }

    @Test
    void transferWithoutRefIsUnmatched() {
        PaymentOutcome out = payments.receive(9L, 50_000, "chuyen tien an trua");

        assertThat(out).isInstanceOf(PaymentOutcome.Unmatched.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_transactions", Integer.class)).isEqualTo(1);
    }
}
