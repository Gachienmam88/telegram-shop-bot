package com.shopbot.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramClientConfig {

    /** Client gọi Telegram Bot API (gửi/sửa/xóa tin nhắn). */
    @Bean
    public TelegramClient telegramClient(ShopProperties props) {
        return new OkHttpTelegramClient(props.bot().token());
    }

    /** Clock inject được để test logic hết hạn không cần chờ thật. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
