package com.shopbot.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import com.shopbot.config.ShopProperties;

/**
 * Tạo link ảnh QR chuyển khoản chuẩn VietQR: app ngân hàng quét là tự điền số TK, số tiền, nội dung CK.
 */
@Service
public class VietQrService {

    private final ShopProperties.Bank bank;

    public VietQrService(ShopProperties props) {
        this.bank = props.bank();
    }

    /**
     * Ảnh QR gửi vào Telegram. Dùng qr.sepay.vn vì trả PNG thật; img.vietqr.io giờ trả SVG (kể cả link .png)
     * mà Telegram sendPhoto không nhận → mất ảnh QR, chỉ còn chữ.
     */
    public String qrImageUrl(long amount, String transferContent) {
        return "https://qr.sepay.vn/img?acc=" + encode(bank.accountNumber())
                + "&bank=" + encode(bank.code())
                + "&amount=" + amount
                + "&des=" + encode(transferContent)
                + "&template=compact";
    }

    /** Trang in đầy đủ thông tin, dùng cho nút "Mở trang thanh toán". */
    public String paymentPageUrl(long amount, String transferContent) {
        return url("print", amount, transferContent);
    }

    private String url(String template, long amount, String content) {
        return "https://img.vietqr.io/image/" + bank.code() + "-" + bank.accountNumber() + "-" + template + ".png"
                + "?amount=" + amount
                + "&addInfo=" + encode(content)
                + "&accountName=" + encode(bank.accountName());
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
