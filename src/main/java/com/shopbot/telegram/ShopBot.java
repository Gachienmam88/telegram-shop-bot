package com.shopbot.telegram;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.shopbot.config.ShopProperties;

import jakarta.annotation.PreDestroy;

/**
 * Nhận update bằng long polling (không cần domain/webhook cho Telegram).
 * Update được chia theo userId vào N luồng đơn: cùng 1 user xử lý tuần tự (không bị bấm đúp tạo 2 đơn),
 * các user khác nhau chạy song song.
 */
@Component
@ConditionalOnProperty(prefix = "shop.bot", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
public class ShopBot implements SpringLongPollingBot, LongPollingUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShopBot.class);
    private static final int STRIPES = 8;

    private final ExecutorService[] stripes = new ExecutorService[STRIPES];
    private final UpdateRouter router;
    private final String token;

    public ShopBot(UpdateRouter router, ShopProperties props) {
        if (props.bot().token() == null || props.bot().token().isBlank()) {
            throw new IllegalStateException("BOT_TOKEN chưa được cấu hình");
        }
        this.router = router;
        this.token = props.bot().token();
        for (int i = 0; i < STRIPES; i++) {
            stripes[i] = Executors.newSingleThreadExecutor();
        }
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            long key = userIdOf(update);
            stripes[(int) Math.floorMod(key, (long) STRIPES)].execute(() -> {
                try {
                    router.route(update);
                } catch (Exception e) {
                    log.error("Unhandled error for update {}", update.getUpdateId(), e);
                }
            });
        }
    }

    private static long userIdOf(Update u) {
        if (u.hasCallbackQuery()) {
            return u.getCallbackQuery().getFrom().getId();
        }
        if (u.hasMessage() && u.getMessage().getFrom() != null) {
            return u.getMessage().getFrom().getId();
        }
        return 0;
    }

    @PreDestroy
    public void shutdown() {
        for (ExecutorService s : stripes) {
            s.shutdown();
        }
    }
}
