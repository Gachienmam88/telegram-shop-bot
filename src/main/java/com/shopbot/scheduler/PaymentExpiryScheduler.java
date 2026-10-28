package com.shopbot.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shopbot.domain.ShopOrder;
import com.shopbot.domain.Topup;
import com.shopbot.service.MessageService;
import com.shopbot.service.OrderService;
import com.shopbot.service.TopupService;
import com.shopbot.service.UserService;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.ShopKeyboards;
import com.shopbot.util.Formats;

/**
 * Mỗi 10 giây: đơn/nạp quá hạn chưa thanh toán → hủy, trả hàng về kho, xóa tin QR (mất ở cả 2 phía) và báo khách.
 * Đọc từ DB nên bot restart giữa chừng vẫn hủy đúng.
 */
@Component
public class PaymentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryScheduler.class);

    private final OrderService orders;
    private final TopupService topups;
    private final UserService users;
    private final TelegramSender sender;
    private final MessageService msg;
    private final ShopKeyboards keyboards;

    public PaymentExpiryScheduler(OrderService orders, TopupService topups, UserService users, TelegramSender sender,
                                  MessageService msg, ShopKeyboards keyboards) {
        this.orders = orders;
        this.topups = topups;
        this.users = users;
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
    }

    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT10S")
    public void expirePayments() {
        for (String code : orders.expiredPendingCodes()) {
            try {
                orders.cancelPending(code, null).ifPresent(this::notifyOrderCancelled);
            } catch (Exception e) {
                log.error("Failed to expire order {}", code, e);
            }
        }
        for (String code : topups.expiredPendingCodes()) {
            try {
                topups.expire(code).ifPresent(this::notifyTopupExpired);
            } catch (Exception e) {
                log.error("Failed to expire topup {}", code, e);
            }
        }
    }

    private void notifyOrderCancelled(ShopOrder o) {
        String lang = langOf(o.getUserId());
        sender.delete(o.getChatId(), o.getPaymentMessageId());
        // Đơn USDT giữ lâu hơn đơn chuyển khoản → tính số phút theo chính đơn đó.
        long minutes = Math.max(1, java.time.Duration.between(o.getCreatedAt(), o.getExpiresAt()).toMinutes());
        sender.send(o.getChatId(), msg.t(lang, "order.cancelled_timeout", o.getCode(), Formats.html(o.getProductName()),
                minutes), keyboards.backHome(lang));
    }

    private void notifyTopupExpired(Topup t) {
        String lang = langOf(t.getUserId());
        sender.delete(t.getChatId(), t.getPaymentMessageId());
        sender.send(t.getChatId(), msg.t(lang, "topup.expired", t.getCode()), keyboards.backHome(lang));
    }

    private String langOf(long userId) {
        return MessageService.normalize(users.get(userId).getLang());
    }
}
