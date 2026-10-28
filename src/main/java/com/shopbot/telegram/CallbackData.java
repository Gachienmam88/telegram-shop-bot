package com.shopbot.telegram;

/** Tiền tố callback_data của nút inline. Dạng "action" hoặc "action:arg" (tối đa 64 byte). */
public final class CallbackData {

    public static final String NOOP = "noop";
    public static final String HOME = "home";
    public static final String LANG = "lang";
    public static final String LANG_MENU = "langs";
    public static final String GUIDE = "guide";
    public static final String WARRANTY = "warr";
    public static final String SUPPORT = "sup";
    public static final String AFFILIATE = "aff";

    /** Lưới danh mục → cat:id (các gói trong danh mục) → prod:id (chi tiết gói). */
    public static final String CATEGORIES = "cats";
    public static final String CATEGORY = "cat";
    public static final String PRODUCT = "prod";

    public static final String PAY_WALLET = "payw";
    public static final String PAY_BANK = "payb";
    public static final String CHECKOUT_CANCEL = "ckx";
    public static final String ORDER_CANCEL = "ocx";
    /** Thanh toán đơn bằng USDT: chọn mạng → paycn:BSC. */
    public static final String PAY_CRYPTO = "payc";
    public static final String PAY_CRYPTO_NETWORK = "paycn";

    public static final String HISTORY = "hist";
    public static final String HISTORY_ORDER = "hord";

    /** Màn hình 2 lựa chọn sau khi chọn ngôn ngữ. */
    public static final String MODE_MENU = "mode";
    public static final String MODE_BUY = "modeb";

    /** Thu mua acc TikTok. */
    public static final String SELL_MENU = "sell";
    public static final String SELL_START = "sells";

    /** Rút tiền. */
    public static final String WITHDRAW = "wd";
    public static final String WITHDRAW_CONFIRM = "wdok";
    public static final String BANK_EDIT = "bank";

    /** Nút admin duyệt/từ chối (hồ sơ bán acc, yêu cầu rút). */
    public static final String SALE_ACCEPT = "asok";
    public static final String SALE_REJECT = "asno";
    public static final String WITHDRAW_PAID = "wdpd";
    public static final String WITHDRAW_REJECT = "wdno";

    public static final String WALLET = "wal";
    public static final String TOPUP = "top";
    public static final String BALANCE_LOG = "blog";
    /** Nạp ví bằng USDT: nhập số USDT → chọn mạng → topcn:BSC. */
    public static final String TOPUP_CRYPTO = "topc";
    public static final String TOPUP_CRYPTO_NETWORK = "topcn";

    private CallbackData() {
    }

    public static String of(String action, Object arg) {
        return action + ":" + arg;
    }
}
