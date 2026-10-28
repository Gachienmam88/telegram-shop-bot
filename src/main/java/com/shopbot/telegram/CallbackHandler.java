package com.shopbot.telegram;

import java.util.List;

/** Handler xử lý 1 nhóm nút inline (theo tiền tố callback_data). */
public interface CallbackHandler {

    /** Các action (phần trước dấu ":") mà handler này xử lý. */
    List<String> actions();

    /** parts = callback_data tách theo ":" (parts[0] là action). */
    void handle(BotContext ctx, String[] parts);
}
