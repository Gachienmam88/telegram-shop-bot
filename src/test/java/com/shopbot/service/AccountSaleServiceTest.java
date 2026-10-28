package com.shopbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shopbot.domain.AccountSubmission;
import com.shopbot.domain.SubmissionStatus;
import com.shopbot.support.PostgresTestBase;

/** Thu mua acc TikTok: duyệt thì cộng đúng giá người bán vào ví; từ chối thì không; không duyệt 2 lần. */
class AccountSaleServiceTest extends PostgresTestBase {

    private static final long USER = 100;
    private static final long ADMIN = 1;

    @Autowired
    AccountSaleService sales;

    @Test
    void acceptCreditsSellerAskingPrice() {
        createUser(USER, 0);
        AccountSubmission s = sales.create(USER, USER, "seller@gmail.com", "@shop", 2_000_000);

        AccountSubmission accepted = sales.accept(s.getCode(), ADMIN);

        assertThat(accepted.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(accepted.getDecidedBy()).isEqualTo(ADMIN);
        assertThat(balanceOf(USER)).isEqualTo(2_000_000);
    }

    @Test
    void rejectDoesNotCredit() {
        createUser(USER, 0);
        AccountSubmission s = sales.create(USER, USER, "0987654321", "@shop", 500_000);

        AccountSubmission rejected = sales.reject(s.getCode(), ADMIN);

        assertThat(rejected.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
        assertThat(balanceOf(USER)).isZero();
    }

    @Test
    void cannotDecideTwice() {
        createUser(USER, 0);
        AccountSubmission s = sales.create(USER, USER, "seller@gmail.com", "@shop", 1_000_000);
        sales.accept(s.getCode(), ADMIN);

        assertThatThrownBy(() -> sales.reject(s.getCode(), ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(balanceOf(USER)).isEqualTo(1_000_000);
    }
}
