package com.shopbot.telegram;

/**
 * Ngữ cảnh 1 update: ai gửi, chat nào, ngôn ngữ gì, tin nhắn bấm nút (nếu là callback).
 *
 * @param messageId id tin nhắn chứa nút vừa bấm; null nếu là tin nhắn gõ tay
 * @param newUser   user vừa được tạo ở update này (dùng gán người giới thiệu)
 */
public record BotContext(long userId, long chatId, String lang, Integer messageId, boolean admin, boolean newUser) {

    public BotContext withLang(String newLang) {
        return new BotContext(userId, chatId, newLang, messageId, admin, newUser);
    }

    /** Bỏ messageId → các màn hình sau sẽ gửi tin mới thay vì sửa tin cũ. */
    public BotContext asNewMessage() {
        return new BotContext(userId, chatId, lang, null, admin, newUser);
    }
}
