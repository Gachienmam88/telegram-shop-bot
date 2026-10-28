package com.shopbot.service;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.BalanceLog;
import com.shopbot.domain.BalanceReason;
import com.shopbot.domain.UserAccount;
import com.shopbot.repository.BalanceLogRepository;
import com.shopbot.repository.UserRepository;

/**
 * Mọi thay đổi số dư đi qua đây: khóa dòng user, đổi số dư, ghi balance_logs trong cùng transaction.
 */
@Service
public class WalletService {

    private final UserRepository users;
    private final BalanceLogRepository logs;
    private final Clock clock;

    public WalletService(UserRepository users, BalanceLogRepository logs, Clock clock) {
        this.users = users;
        this.logs = logs;
        this.clock = clock;
    }

    /** Cộng tiền, trả về số dư mới. */
    @Transactional
    public long credit(long userId, long amount, BalanceReason reason, String ref) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return apply(lock(userId), amount, reason, ref);
    }

    /** Trừ tiền, ném ShopException nếu không đủ số dư. Trả về số dư mới. */
    @Transactional
    public long debit(long userId, long amount, BalanceReason reason, String ref) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        UserAccount u = lock(userId);
        if (u.getBalance() < amount) {
            throw new ShopException("checkout.insufficient");
        }
        return apply(u, -amount, reason, ref);
    }

    /** Admin cộng (delta > 0) hoặc trừ (delta < 0). */
    @Transactional
    public long adjust(long userId, long delta, String ref) {
        return delta >= 0
                ? credit(userId, delta, BalanceReason.ADMIN, ref)
                : debit(userId, -delta, BalanceReason.ADMIN, ref);
    }

    @Transactional(readOnly = true)
    public long balance(long userId) {
        return users.findById(userId).map(UserAccount::getBalance).orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<BalanceLog> recentLogs(long userId) {
        return logs.findTop15ByUserIdOrderByIdDesc(userId);
    }

    private UserAccount lock(long userId) {
        return users.findForUpdate(userId).orElseThrow(() -> new ShopException("common.user_not_found"));
    }

    private long apply(UserAccount u, long delta, BalanceReason reason, String ref) {
        u.setBalance(u.getBalance() + delta);
        BalanceLog log = new BalanceLog();
        log.setUserId(u.getUserId());
        log.setDelta(delta);
        log.setBalanceAfter(u.getBalance());
        log.setReason(reason);
        log.setRef(ref);
        log.setCreatedAt(clock.instant());
        logs.save(log);
        return u.getBalance();
    }
}
