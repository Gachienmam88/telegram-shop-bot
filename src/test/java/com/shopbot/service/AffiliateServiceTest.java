package com.shopbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shopbot.support.PostgresTestBase;

class AffiliateServiceTest extends PostgresTestBase {

    @Autowired
    AffiliateService affiliate;

    @Autowired
    UserService users;

    @Autowired
    OrderService orders;

    @Test
    void noCommissionWhileDisabled() {
        createUser(1, 0);
        createUser(2, 1_000_000);
        users.assignReferrer(2, 1);
        long productId = createProduct(200_000, 1);

        orders.payWithWallet(2, 2, productId, 1);

        assertThat(affiliate.percent()).isZero();
        assertThat(balanceOf(1)).isZero();
    }

    @Test
    void referrerEarnsPercentOfPaidOrders() {
        createUser(1, 0);
        createUser(2, 1_000_000);
        users.assignReferrer(2, 1);
        affiliate.setPercent(5);
        long productId = createProduct(200_000, 2);

        orders.payWithWallet(2, 2, productId, 2);

        assertThat(balanceOf(1)).isEqualTo(20_000);
        AffiliateService.Stats stats = affiliate.stats(1);
        assertThat(stats.referredCount()).isEqualTo(1);
        assertThat(stats.totalCommission()).isEqualTo(20_000);
    }

    @Test
    void referrerRules() {
        createUser(1, 0);
        createUser(2, 0);
        createUser(3, 0);

        assertThat(users.assignReferrer(1, 1)).as("không tự giới thiệu").isFalse();
        assertThat(users.assignReferrer(2, 999)).as("người giới thiệu phải tồn tại").isFalse();
        assertThat(users.assignReferrer(2, 1)).isTrue();
        assertThat(users.assignReferrer(2, 3)).as("chỉ gán 1 lần").isFalse();
    }
}
