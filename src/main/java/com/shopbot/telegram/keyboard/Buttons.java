package com.shopbot.telegram.keyboard;

import java.util.Arrays;
import java.util.List;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

/** Hàm tiện ích tạo nút / hàng nút inline. */
public final class Buttons {

    private Buttons() {
    }

    public static InlineKeyboardButton cb(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    public static InlineKeyboardButton url(String text, String url) {
        return InlineKeyboardButton.builder().text(text).url(url).build();
    }

    public static InlineKeyboardRow row(InlineKeyboardButton... buttons) {
        return new InlineKeyboardRow(Arrays.asList(buttons));
    }

    public static InlineKeyboardMarkup markup(List<InlineKeyboardRow> rows) {
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup markup(InlineKeyboardRow... rows) {
        return new InlineKeyboardMarkup(Arrays.asList(rows));
    }
}
