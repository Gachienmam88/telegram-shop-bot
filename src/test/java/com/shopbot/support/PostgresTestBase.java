package com.shopbot.support;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.testcontainers.containers.PostgreSQLContainer;

import com.shopbot.scheduler.CryptoDepositScheduler;
import com.shopbot.scheduler.PaymentExpiryScheduler;

/**
 * Test tích hợp chạy trên PostgreSQL thật (Testcontainers) — cần Docker.
 * Telegram được mock (không gửi tin thật), scheduler hết hạn bị tắt để test tự điều khiển thời gian.
 */
@SpringBootTest(properties = {
        "shop.name=AI SHOP",
        "shop.bot.polling-enabled=false",
        "shop.bot.notify-chat-id=",
        "shop.bot.support-username=support_test",
        "shop.bot.token=123:test",
        "shop.bot.username=test_bot",
        "shop.bot.admin-ids=1",
        "shop.sepay.api-key=test-key",
        "shop.bank.account-number=0366189136",
        "shop.bank.account-name=NGUYEN VAN A",
        "shop.payment.timeout-seconds=300",
        "shop.payment.usdt-rate=27000",
        "shop.crypto.timeout-seconds=1800",
        "shop.crypto.bsc-address=0x5ab0000000000000000000000000000000000001",
        "shop.crypto.polygon-address=0x5ab0000000000000000000000000000000000001",
        "shop.crypto.tron-address=TJEif21DCjR1HwfdFK48Mk3XWzfoDHpTYX"
})
@Import(PostgresTestBase.TestClockConfig.class)
public abstract class PostgresTestBase {

    /** 1 container dùng chung cho mọi test class (khởi động 1 lần). */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @MockitoBean
    protected TelegramClient telegramClient;

    @MockitoBean
    protected PaymentExpiryScheduler expiryScheduler;

    /** Không gọi blockchain thật trong test: giao dịch USDT được đưa vào trực tiếp qua CryptoPaymentService. */
    @MockitoBean
    protected CryptoDepositScheduler cryptoDepositScheduler;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected Clock clock;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE balance_logs, bank_transactions, crypto_transfers, crypto_invoices, conversation_states,"
                + " account_submissions, withdrawals, stock_items, orders, topups, products, categories, users"
                + " RESTART IDENTITY CASCADE");
        jdbc.update("UPDATE app_settings SET value = '0' WHERE key = 'affiliate_percent'");
    }

    protected MutableClock mutableClock() {
        return (MutableClock) clock;
    }

    protected void createUser(long userId, long balance) {
        jdbc.update("INSERT INTO users (user_id, username, lang, balance) VALUES (?, ?, 'vi', ?)",
                userId, "user" + userId, balance);
    }

    /** Tạo danh mục + SP giá price, kèm stockCount tài khoản "acc1|pass1"... Trả về productId. */
    protected long createProduct(long price, int stockCount) {
        Long categoryId = jdbc.queryForObject("INSERT INTO categories (emoji, name) VALUES ('🎬', 'Test') RETURNING id",
                Long.class);
        Long productId = jdbc.queryForObject(
                "INSERT INTO products (category_id, emoji, name, price) VALUES (?, '✂️', 'CapCut Pro', ?) RETURNING id",
                Long.class, categoryId, price);
        for (int i = 1; i <= stockCount; i++) {
            jdbc.update("INSERT INTO stock_items (product_id, content, status) VALUES (?, ?, 'AVAILABLE')",
                    productId, "acc" + i + "|pass" + i);
        }
        return productId;
    }

    protected long balanceOf(long userId) {
        return jdbc.queryForObject("SELECT balance FROM users WHERE user_id = ?", Long.class, userId);
    }

    protected int stockCount(long productId, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM stock_items WHERE product_id = ? AND status = ?",
                Integer.class, productId, status);
    }
}
