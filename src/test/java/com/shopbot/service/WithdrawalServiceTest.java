package com.shopbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shopbot.domain.Withdrawal;
import com.shopbot.domain.WithdrawalStatus;
import com.shopbot.support.PostgresTestBase;

/** Rút tiền: trừ ví khi tạo, từ chối thì hoàn, duyệt rồi người dùng xác nhận; thiếu TK ngân hàng thì chặn. */
class WithdrawalServiceTest extends PostgresTestBase {

    private static final long USER = 100;
    private static final long ADMIN = 1;

    @Autowired
    WithdrawalService withdrawals;

    @Autowired
    UserService users;

    private void withBank(long userId) {
        users.setBankInfo(userId, "MB Bank", "0366189136", "NGUYEN VAN A");
    }

    @Test
    void requestDebitsWalletAndSnapshotsBank() {
        createUser(USER, 500_000);
        withBank(USER);

        Withdrawal w = withdrawals.request(USER, USER, 300_000);

        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.PENDING);
        assertThat(w.getBankAccountNumber()).isEqualTo("0366189136");
        assertThat(balanceOf(USER)).isEqualTo(200_000);
    }

    @Test
    void requestWithoutBankInfoIsRejected() {
        createUser(USER, 500_000);

        assertThatThrownBy(() -> withdrawals.request(USER, USER, 100_000))
                .isInstanceOf(ShopException.class)
                .satisfies(e -> assertThat(((ShopException) e).messageKey()).isEqualTo("withdraw.no_bank"));
        assertThat(balanceOf(USER)).isEqualTo(500_000);
    }

    @Test
    void insufficientBalanceIsRejected() {
        createUser(USER, 50_000);
        withBank(USER);

        assertThatThrownBy(() -> withdrawals.request(USER, USER, 100_000))
                .isInstanceOf(ShopException.class);
        assertThat(balanceOf(USER)).isEqualTo(50_000);
    }

    @Test
    void rejectRefundsBalance() {
        createUser(USER, 500_000);
        withBank(USER);
        Withdrawal w = withdrawals.request(USER, USER, 300_000);

        Withdrawal rejected = withdrawals.reject(w.getCode(), ADMIN);

        assertThat(rejected.getStatus()).isEqualTo(WithdrawalStatus.REJECTED);
        assertThat(balanceOf(USER)).isEqualTo(500_000);
    }

    @Test
    void paidThenUserConfirms() {
        createUser(USER, 500_000);
        withBank(USER);
        Withdrawal w = withdrawals.request(USER, USER, 300_000);

        withdrawals.markPaid(w.getCode(), ADMIN);
        Withdrawal confirmed = withdrawals.confirm(w.getCode(), USER);

        assertThat(confirmed.getStatus()).isEqualTo(WithdrawalStatus.CONFIRMED);
        assertThat(balanceOf(USER)).isEqualTo(200_000);
    }

    @Test
    void cannotConfirmBeforePaid() {
        createUser(USER, 500_000);
        withBank(USER);
        Withdrawal w = withdrawals.request(USER, USER, 300_000);

        assertThatThrownBy(() -> withdrawals.confirm(w.getCode(), USER))
                .isInstanceOf(ShopException.class);
    }
}
