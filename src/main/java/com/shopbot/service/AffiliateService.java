package com.shopbot.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.BalanceReason;
import com.shopbot.domain.ShopOrder;
import com.shopbot.repository.BalanceLogRepository;
import com.shopbot.repository.UserRepository;

/**
 * Affiliate: người giới thiệu nhận % giá trị mỗi đơn đã thanh toán của người được giới thiệu.
 * Tỷ lệ lưu ở app_settings.affiliate_percent, mặc định 0 = chưa kích hoạt.
 */
@Service
public class AffiliateService {

    /** Thống kê hiển thị ở màn Affiliate. */
    public record Stats(long referredCount, long totalCommission, int percent) {
    }

    private final SettingsService settings;
    private final UserRepository users;
    private final BalanceLogRepository logs;
    private final WalletService wallet;

    public AffiliateService(SettingsService settings, UserRepository users, BalanceLogRepository logs,
                            WalletService wallet) {
        this.settings = settings;
        this.users = users;
        this.logs = logs;
        this.wallet = wallet;
    }

    public int percent() {
        return settings.getInt(SettingsService.AFFILIATE_PERCENT, 0);
    }

    public void setPercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be 0..100");
        }
        settings.set(SettingsService.AFFILIATE_PERCENT, String.valueOf(percent));
    }

    /** Gọi trong transaction của đơn vừa thanh toán. */
    @Transactional
    public void onOrderPaid(ShopOrder order) {
        int pct = percent();
        if (pct <= 0) {
            return;
        }
        Long referrerId = users.findById(order.getUserId()).map(u -> u.getReferrerId()).orElse(null);
        if (referrerId == null) {
            return;
        }
        long commission = order.getAmount() * pct / 100;
        if (commission > 0) {
            wallet.credit(referrerId, commission, BalanceReason.AFFILIATE, order.getCode());
        }
    }

    @Transactional(readOnly = true)
    public Stats stats(long userId) {
        return new Stats(users.countByReferrerId(userId), logs.sumDelta(userId, BalanceReason.AFFILIATE), percent());
    }
}
