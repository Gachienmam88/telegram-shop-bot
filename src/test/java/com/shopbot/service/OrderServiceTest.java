package com.shopbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shopbot.domain.OrderStatus;
import com.shopbot.domain.ShopOrder;
import com.shopbot.support.PostgresTestBase;

class OrderServiceTest extends PostgresTestBase {

    @Autowired
    OrderService orders;

    @Test
    void payWithWalletDeductsBalanceAndDeliversAccounts() {
        createUser(100, 1_000_000);
        long productId = createProduct(270_000, 5);

        PaidOrder paid = orders.payWithWallet(100, 100, productId, 2);

        assertThat(paid.accounts()).containsExactly("acc1|pass1", "acc2|pass2");
        assertThat(paid.order().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(balanceOf(100)).isEqualTo(460_000);
        assertThat(stockCount(productId, "SOLD")).isEqualTo(2);
        assertThat(stockCount(productId, "AVAILABLE")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT sold FROM products WHERE id = ?", Integer.class, productId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT delta FROM balance_logs WHERE user_id = 100", Long.class))
                .isEqualTo(-540_000);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE code = ?", String.class,
                paid.order().getCode())).isEqualTo("PAID");
    }

    @Test
    void payWithWalletFailsWhenBalanceTooLow() {
        createUser(100, 100_000);
        long productId = createProduct(270_000, 5);

        assertThatThrownBy(() -> orders.payWithWallet(100, 100, productId, 1))
                .isInstanceOf(ShopException.class).hasMessage("checkout.insufficient");
        assertThat(balanceOf(100)).isEqualTo(100_000);
        assertThat(stockCount(productId, "AVAILABLE")).isEqualTo(5);
    }

    @Test
    void notEnoughStockRollsBackWalletDebit() {
        createUser(100, 10_000_000);
        long productId = createProduct(100_000, 2);

        assertThatThrownBy(() -> orders.payWithWallet(100, 100, productId, 3))
                .isInstanceOf(ShopException.class).hasMessage("product.not_enough_stock");
        assertThat(balanceOf(100)).isEqualTo(10_000_000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM balance_logs", Integer.class)).isZero();
        assertThat(stockCount(productId, "AVAILABLE")).isEqualTo(2);
    }

    @Test
    void bankOrderReservesStockAndCancelReleasesIt() {
        createUser(100, 0);
        long productId = createProduct(100_000, 3);

        ShopOrder o = orders.createBankOrder(100, 100, productId, 2);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.getPayRef()).matches("DH[A-Z0-9]{8}");
        assertThat(stockCount(productId, "RESERVED")).isEqualTo(2);

        assertThat(orders.cancelPending(o.getCode(), 999L)).as("người khác không hủy được").isEmpty();
        assertThat(orders.cancelPending(o.getCode(), 100L)).isPresent();
        assertThat(stockCount(productId, "AVAILABLE")).isEqualTo(3);
        assertThat(orders.cancelPending(o.getCode(), 100L)).as("hủy lần 2 không có tác dụng").isEmpty();
    }

    @Test
    void pendingOrderExpiresAfterTimeout() {
        createUser(100, 0);
        long productId = createProduct(100_000, 1);
        ShopOrder o = orders.createBankOrder(100, 100, productId, 1);

        mutableClock().advance(Duration.ofSeconds(299));
        assertThat(orders.expiredPendingCodes()).isEmpty();

        mutableClock().advance(Duration.ofSeconds(2));
        assertThat(orders.expiredPendingCodes()).containsExactly(o.getCode());
    }

    @Test
    void concurrentBuyersCannotReserveSameLastAccount() throws Exception {
        createUser(100, 0);
        createUser(200, 0);
        long productId = createProduct(100_000, 1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = new ArrayList<>();
        for (long user : new long[] {100, 200}) {
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    orders.createBankOrder(user, user, productId, 1);
                    return true;
                } catch (ShopException e) {
                    return false;
                }
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        int success = 0;
        for (Future<Boolean> f : results) {
            success += f.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(success).isEqualTo(1);
        assertThat(stockCount(productId, "RESERVED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void customerCanOnlySeeOwnPaidOrder() {
        createUser(100, 1_000_000);
        long productId = createProduct(100_000, 2);
        PaidOrder paid = orders.payWithWallet(100, 100, productId, 1);

        assertThat(orders.paidOrderOf(100, paid.order().getCode()).accounts()).containsExactly("acc1|pass1");
        assertThatThrownBy(() -> orders.paidOrderOf(200, paid.order().getCode()))
                .isInstanceOf(ShopException.class).hasMessage("history.not_found");
    }
}
