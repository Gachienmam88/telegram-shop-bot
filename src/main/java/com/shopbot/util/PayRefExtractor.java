package com.shopbot.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tìm mã thanh toán (DHxxxxxxxx / NAPxxxxxxxx) trong nội dung chuyển khoản.
 * Ngân hàng hay chèn thêm chữ (tên người chuyển, mã GD...) nên chỉ tìm theo regex, không so cả chuỗi.
 */
public final class PayRefExtractor {

    private static final Pattern REF = Pattern.compile("(DH|NAP)[A-Z0-9]{8}");

    private PayRefExtractor() {
    }

    public static Optional<String> find(String content) {
        if (content == null) {
            return Optional.empty();
        }
        Matcher m = REF.matcher(content.toUpperCase(Locale.ROOT));
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }
}
