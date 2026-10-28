package com.shopbot.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Định dạng tiền, thời gian, escape HTML cho tin nhắn Telegram. */
public final class Formats {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yy");

    private Formats() {
    }

    /** 270000 → "270.000đ". */
    public static String money(long vnd) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        String formatted = new DecimalFormat("#,##0", symbols).format(Math.abs(vnd)) + "đ";
        return vnd < 0 ? "-" + formatted : formatted;
    }

    /**
     * Giá hiển thị gọn: 50000 → "50k", 1650000 → "1650k".
     * Giá không chẵn nghìn (vd 12345) thì ghi đủ "12.345đ" để không làm tròn sai giá.
     */
    public static String price(long vnd) {
        if (vnd >= 1000 && vnd % 1000 == 0) {
            return (vnd / 1000) + "k";
        }
        return money(vnd);
    }

    /** 100000 → "+100.000đ", -50000 → "-50.000đ". */
    public static String signedMoney(long vnd) {
        return vnd >= 0 ? "+" + money(vnd) : money(vnd);
    }

    /** Quy đổi VND sang USDT, 2 chữ số thập phân. */
    public static String usdt(long vnd, BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) {
            return "0";
        }
        return BigDecimal.valueOf(vnd).divide(rate, 2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String time(Instant instant, ZoneId zone) {
        return instant == null ? "" : TIME.format(instant.atZone(zone));
    }

    /** Escape ký tự đặc biệt khi chèn chuỗi người dùng/admin vào tin nhắn parse_mode=HTML. */
    public static String html(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
