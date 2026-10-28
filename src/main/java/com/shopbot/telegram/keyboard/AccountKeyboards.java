package com.shopbot.telegram.keyboard;

import static com.shopbot.telegram.keyboard.Buttons.cb;
import static com.shopbot.telegram.keyboard.Buttons.row;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import com.shopbot.service.MessageService;
import com.shopbot.telegram.CallbackData;

/** Bàn phím cho màn hình 2 lựa chọn, thu mua acc TikTok, rút tiền và nút duyệt của admin. */
@Component
public class AccountKeyboards {

    private final MessageService msg;

    public AccountKeyboards(MessageService msg) {
        this.msg = msg;
    }

    /** 2 lựa chọn sau khi chọn ngôn ngữ. */
    public InlineKeyboardMarkup modeMenu(String lang) {
        return Buttons.markup(
                row(cb(msg.t(lang, "btn.mode_buy"), CallbackData.MODE_BUY)),
                row(cb(msg.t(lang, "btn.mode_sell"), CallbackData.SELL_MENU)),
                row(cb(msg.t(lang, "btn.language"), CallbackData.LANG_MENU)));
    }

    /** Màn hình giới thiệu bán acc → nút Bắt đầu gửi. */
    public InlineKeyboardMarkup sellIntro(String lang) {
        return Buttons.markup(
                row(cb(msg.t(lang, "btn.sell_start"), CallbackData.SELL_START)),
                row(cb(msg.t(lang, "btn.home"), CallbackData.HOME)));
    }

    /** Nút Duyệt / Từ chối gửi cho admin khi có hồ sơ bán acc. */
    public InlineKeyboardMarkup saleReview(String code) {
        return Buttons.markup(row(
                cb("✅ Duyệt", CallbackData.of(CallbackData.SALE_ACCEPT, code)),
                cb("❌ Từ chối", CallbackData.of(CallbackData.SALE_REJECT, code))));
    }

    /** Nút Đã chuyển / Từ chối gửi cho admin khi có yêu cầu rút. */
    public InlineKeyboardMarkup withdrawReview(String code) {
        return Buttons.markup(row(
                cb("✅ Đã chuyển", CallbackData.of(CallbackData.WITHDRAW_PAID, code)),
                cb("❌ Từ chối", CallbackData.of(CallbackData.WITHDRAW_REJECT, code))));
    }

    /** Nút để người dùng xác nhận đã nhận tiền. */
    public InlineKeyboardMarkup withdrawConfirm(String lang, String code) {
        return Buttons.markup(
                row(cb(msg.t(lang, "btn.received"), CallbackData.of(CallbackData.WITHDRAW_CONFIRM, code))),
                row(cb(msg.t(lang, "btn.home"), CallbackData.HOME)));
    }
}
