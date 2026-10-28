package com.shopbot.config;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Toàn bộ cấu hình của shop, đọc từ application.yml (giá trị thật lấy từ biến môi trường / .env).
 */
@ConfigurationProperties(prefix = "shop")
public record ShopProperties(String name, String timezone, Bot bot, Bank bank, Sepay sepay, Payment payment,
                             Crypto crypto) {

    /** Cấu hình Telegram bot. */
    public record Bot(String token, String username, List<Long> adminIds, String supportUsername,
                      String notifyChatId, boolean pollingEnabled) {
    }

    /** Tài khoản ngân hàng nhận tiền (dùng để tạo VietQR). */
    public record Bank(String code, String displayName, String accountNumber, String accountName) {
    }

    /** SePay webhook. */
    public record Sepay(String apiKey) {
    }

    /** Thời gian giữ đơn, giới hạn nạp, tỷ giá USDT. */
    public record Payment(int timeoutSeconds, long topupMin, long topupMax, BigDecimal usdtRate) {
    }

    /**
     * Ví nhận USDT. Địa chỉ trống = tắt mạng đó. timeoutSeconds: thời gian giữ đơn/nạp chờ chuyển USDT.
     */
    public record Crypto(int timeoutSeconds, String bscAddress, String bscRpc, String polygonAddress,
                         String polygonRpc, String tronAddress, String trongridApiKey) {
    }

    public boolean isAdmin(long userId) {
        return bot.adminIds() != null && bot.adminIds().contains(userId);
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public int timeoutMinutes() {
        return Math.max(1, payment.timeoutSeconds() / 60);
    }

    /** Deep link mở bot, payload tùy chọn (vd: p_12, ref_123). */
    public String deepLink(String payload) {
        return "https://t.me/" + bot.username() + (payload == null ? "" : "?start=" + payload);
    }
}
