package com.shopbot.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.BalanceReason;
import com.shopbot.domain.UserAccount;
import com.shopbot.domain.Withdrawal;
import com.shopbot.domain.WithdrawalStatus;
import com.shopbot.repository.WithdrawalRepository;
import com.shopbot.util.CodeGenerator;

/**
 * Rút tiền (không chiết khấu): tạo yêu cầu trừ ví ngay, admin bấm đã chuyển, người dùng xác nhận đã nhận.
 * Từ chối thì hoàn lại số dư.
 */
@Service
public class WithdrawalService {

    /** Số tiền rút tối thiểu mỗi lần. */
    public static final long MIN_AMOUNT = 10_000;

    private final WithdrawalRepository withdrawals;
    private final WalletService wallet;
    private final UserService users;
    private final CodeGenerator codes;
    private final Clock clock;

    public WithdrawalService(WithdrawalRepository withdrawals, WalletService wallet, UserService users,
                             CodeGenerator codes, Clock clock) {
        this.withdrawals = withdrawals;
        this.wallet = wallet;
        this.users = users;
        this.codes = codes;
        this.clock = clock;
    }

    /** Tạo yêu cầu rút: trừ ví trước (ném ShopException nếu không đủ), chụp lại thông tin ngân hàng. */
    @Transactional
    public Withdrawal request(long userId, long chatId, long amount) {
        UserAccount u = users.get(userId);
        if (!u.hasBankInfo()) {
            throw new ShopException("withdraw.no_bank");
        }
        if (amount < MIN_AMOUNT) {
            throw new ShopException("withdraw.too_small");
        }
        String code = codes.payRef(CodeGenerator.WITHDRAW_PREFIX);
        wallet.debit(userId, amount, BalanceReason.WITHDRAW, code);
        Withdrawal w = new Withdrawal();
        w.setCode(code);
        w.setUserId(userId);
        w.setAmount(amount);
        w.setBankName(u.getBankName());
        w.setBankAccountNumber(u.getBankAccountNumber());
        w.setBankAccountHolder(u.getBankAccountHolder());
        w.setStatus(WithdrawalStatus.PENDING);
        w.setChatId(chatId);
        w.setCreatedAt(clock.instant());
        return withdrawals.save(w);
    }

    /** Admin đã chuyển khoản. */
    @Transactional
    public Withdrawal markPaid(String code, long adminId) {
        Withdrawal w = inStatus(code, WithdrawalStatus.PENDING);
        w.setStatus(WithdrawalStatus.PAID);
        w.setPaidAt(clock.instant());
        w.setDecidedBy(adminId);
        return w;
    }

    /** Người dùng xác nhận đã nhận tiền. */
    @Transactional
    public Withdrawal confirm(String code, long userId) {
        Withdrawal w = withdrawals.findByCode(code)
                .orElseThrow(() -> new ShopException("withdraw.not_found"));
        if (w.getUserId() != userId) {
            throw new ShopException("withdraw.not_found");
        }
        if (w.getStatus() != WithdrawalStatus.PAID) {
            throw new ShopException("withdraw.not_payable");
        }
        w.setStatus(WithdrawalStatus.CONFIRMED);
        w.setConfirmedAt(clock.instant());
        return w;
    }

    /** Từ chối: hoàn lại số dư cho người dùng. */
    @Transactional
    public Withdrawal reject(String code, long adminId) {
        Withdrawal w = inStatus(code, WithdrawalStatus.PENDING);
        w.setStatus(WithdrawalStatus.REJECTED);
        w.setDecidedBy(adminId);
        wallet.credit(w.getUserId(), w.getAmount(), BalanceReason.WITHDRAW_REFUND, w.getCode());
        return w;
    }

    private Withdrawal inStatus(String code, WithdrawalStatus expected) {
        Withdrawal w = withdrawals.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút " + code));
        if (w.getStatus() != expected) {
            throw new IllegalArgumentException("Yêu cầu rút " + code + " đã được xử lý rồi");
        }
        return w;
    }
}
