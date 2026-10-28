package com.shopbot.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopbot.config.ShopProperties;
import com.shopbot.service.IncomingPaymentService;

/**
 * Nhận webhook SePay. Xác thực header "Authorization: Apikey &lt;SEPAY_API_KEY&gt;".
 * Trả {"success": true} để SePay không gửi lại; trả lỗi 5xx khi xử lý hỏng để SePay retry.
 */
@RestController
@RequestMapping("/sepay")
public class SepayWebhookController {

    private static final Map<String, Object> OK = Map.of("success", true);

    private final IncomingPaymentService payments;
    private final ShopProperties props;

    public SepayWebhookController(IncomingPaymentService payments, ShopProperties props) {
        this.payments = payments;
        this.props = props;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SepayWebhookPayload payload) {
        if (!authorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false));
        }
        if (!"in".equalsIgnoreCase(payload.transferType()) || payload.id() == null
                || payload.transferAmount() == null) {
            return ResponseEntity.ok(OK);
        }
        // SePay có thể tách sẵn mã thanh toán vào "code" → ghép cả 2 để tìm mã.
        String content = (payload.content() == null ? "" : payload.content())
                + " " + (payload.code() == null ? "" : payload.code());
        payments.receive(payload.id(), payload.transferAmount(), content.trim());
        return ResponseEntity.ok(OK);
    }

    private boolean authorized(String header) {
        String key = props.sepay().apiKey();
        if (key == null || key.isBlank() || header == null) {
            return false;
        }
        String expected = "Apikey " + key.trim();
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                header.trim().getBytes(StandardCharsets.UTF_8));
    }
}
