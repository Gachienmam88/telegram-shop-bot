package com.shopbot.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.CryptoNetwork;
import com.shopbot.service.CryptoWallets;
import com.shopbot.service.SettingsService;
import com.shopbot.service.crypto.CryptoPaymentService;
import com.shopbot.service.crypto.EvmUsdtTransferSource;
import com.shopbot.service.crypto.IncomingUsdtTransfer;
import com.shopbot.service.crypto.TronUsdtTransferSource;
import com.shopbot.service.crypto.UsdtTransferSource;

/**
 * Mỗi 15 giây đọc giao dịch USDT mới vào các ví đã cấu hình và xử lý thanh toán.
 * Con trỏ từng mạng lưu ở app_settings → bot restart vẫn đọc tiếp, không bỏ sót.
 * Chỉ tiến con trỏ khi cả lô xử lý xong; lỗi → lần sau đọc lại (đã chống trùng theo tx).
 */
@Component
public class CryptoDepositScheduler {

    private static final Logger log = LoggerFactory.getLogger(CryptoDepositScheduler.class);
    private static final String CURSOR_KEY = "crypto_cursor_";
    /** Tối đa số lô mỗi mạng mỗi lần chạy (bắt kịp dần nếu bot tắt lâu). */
    private static final int MAX_BATCHES_PER_TICK = 5;

    private final List<UsdtTransferSource> sources = new ArrayList<>();
    private final CryptoPaymentService payments;
    private final SettingsService settings;

    public CryptoDepositScheduler(CryptoWallets wallets, ShopProperties props, CryptoPaymentService payments,
                                  SettingsService settings, Clock clock) {
        this.payments = payments;
        this.settings = settings;
        ShopProperties.Crypto c = props.crypto();
        for (CryptoNetwork n : wallets.enabledNetworks()) {
            sources.add(switch (n) {
                case BSC -> new EvmUsdtTransferSource(n, wallets.address(n), http(c.bscRpc(), null));
                case POLYGON -> new EvmUsdtTransferSource(n, wallets.address(n), http(c.polygonRpc(), null));
                case TRON -> new TronUsdtTransferSource(wallets.address(n),
                        http("https://api.trongrid.io", c.trongridApiKey()), clock);
            });
        }
        log.info("USDT watcher networks: {}", wallets.enabledNetworks());
    }

    @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT20S")
    public void poll() {
        for (UsdtTransferSource source : sources) {
            try {
                pollNetwork(source);
            } catch (Exception e) {
                log.warn("USDT watcher {} error: {}", source.network(), e.getMessage());
            }
        }
    }

    void pollNetwork(UsdtTransferSource source) {
        String key = CURSOR_KEY + source.network().name();
        Long cursor = settings.getLong(key).orElse(null);
        for (int i = 0; i < MAX_BATCHES_PER_TICK; i++) {
            UsdtTransferSource.Batch batch = source.fetch(cursor);
            for (IncomingUsdtTransfer t : batch.transfers()) {
                payments.receive(t);
            }
            if (cursor == null || batch.nextCursor() != cursor) {
                settings.set(key, String.valueOf(batch.nextCursor()));
            }
            cursor = batch.nextCursor();
            if (!batch.hasMore()) {
                return;
            }
        }
    }

    private static RestClient http(String baseUrl, String tronApiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        RestClient.Builder b = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        if (tronApiKey != null && !tronApiKey.isBlank()) {
            b.defaultHeader("TRON-PRO-API-KEY", tronApiKey.trim());
        }
        return b.build();
    }
}
