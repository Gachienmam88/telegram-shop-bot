package com.shopbot.service;

/**
 * Lỗi nghiệp vụ hiển thị được cho người dùng: messageKey là key i18n, args là tham số của câu.
 */
public class ShopException extends RuntimeException {

    private final transient Object[] args;

    public ShopException(String messageKey, Object... args) {
        super(messageKey);
        this.args = args;
    }

    public String messageKey() {
        return getMessage();
    }

    public Object[] args() {
        return args;
    }
}
