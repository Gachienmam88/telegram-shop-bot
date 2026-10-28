package com.shopbot.telegram;

import org.springframework.stereotype.Component;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.ShopOrder;
import com.shopbot.domain.Topup;
import com.shopbot.service.MessageService;
import com.shopbot.service.PaymentOutcome;
import com.shopbot.service.UserService;
import com.shopbot.telegram.keyboard.PaymentKeyboards;
import com.shopbot.util.Formats;

/** Báo kết quả thanh toán cho khách (giao hàng, cộng ví, xóa tin QR) và báo admin giao dịch lạ. */
@Component
public class PaymentOutcomeNotifier {

    private final TelegramSender sender;
    private final MessageService msg;
    private final PaymentKeyboards keyboards;
    private final OrderDeliveryMessenger delivery;
    private final UserService users;
    private final ShopProperties props;

    public PaymentOutcomeNotifier(TelegramSender sender, MessageService msg, PaymentKeyboards keyboards,
                                  OrderDeliveryMessenger delivery, UserService users, ShopProperties props) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
        this.delivery = delivery;
        this.users = users;
        this.props = props;
    }

    public void notify(PaymentOutcome outcome) {
        if (outcome instanceof PaymentOutcome.OrderPaid p) {
            ShopOrder o = p.paid().order();
            String lang = langOf(o.getUserId());
            sender.delete(o.getChatId(), o.getPaymentMessageId());
            delivery.deliver(o.getChatId(), lang, p.paid());
            if (p.overpaid() > 0) {
                sender.send(o.getChatId(), msg.t(lang, "order.overpaid", Formats.money(p.overpaid())), null);
            }
        } else if (outcome instanceof PaymentOutcome.OrderUnderpaid u) {
            ShopOrder o = u.order();
            String lang = langOf(o.getUserId());
            sender.delete(o.getChatId(), o.getPaymentMessageId());
            sender.send(o.getChatId(), msg.t(lang, "order.underpaid", o.getCode(), Formats.money(u.received()),
                    Formats.money(o.getAmount()), Formats.money(u.balance())), keyboards.backWallet(lang));
        } else if (outcome instanceof PaymentOutcome.LatePayment l) {
            ShopOrder o = l.order();
            String lang = langOf(o.getUserId());
            sender.send(o.getChatId(), msg.t(lang, "order.late_payment", Formats.money(l.received()), o.getCode(),
                    Formats.money(l.balance())), keyboards.backWallet(lang));
        } else if (outcome instanceof PaymentOutcome.TopupPaid t) {
            Topup topup = t.topup();
            String lang = langOf(topup.getUserId());
            if (t.wasPending()) {
                sender.delete(topup.getChatId(), topup.getPaymentMessageId());
            }
            sender.send(topup.getChatId(), msg.t(lang, "topup.success", Formats.money(t.credited()),
                    Formats.money(t.balance())), keyboards.backWallet(lang));
        } else if (outcome instanceof PaymentOutcome.Unmatched u) {
            notifyAdmins("⚠️ <b>Giao dịch không khớp mã</b>\nSố tiền: " + Formats.money(u.amount())
                    + "\nNội dung: <code>" + Formats.html(u.content()) + "</code>\n\nKiểm tra và dùng /addbal hoặc /confirm nếu cần.");
        }
    }

    public void notifyAdmins(String html) {
        notifyAdmins(html, null);
    }

    /** Gửi mọi admin 1 tin kèm bàn phím (vd nút Duyệt/Từ chối). */
    public void notifyAdmins(String html, org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup kb) {
        if (props.bot().adminIds() == null) {
            return;
        }
        props.bot().adminIds().forEach(id -> sender.send(id, html, kb));
    }

    private String langOf(long userId) {
        return MessageService.normalize(users.get(userId).getLang());
    }
}
