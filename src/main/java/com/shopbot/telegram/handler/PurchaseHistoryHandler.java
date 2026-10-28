package com.shopbot.telegram.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.shopbot.domain.ShopOrder;
import com.shopbot.service.MessageService;
import com.shopbot.service.OrderService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.OrderDeliveryMessenger;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.PaymentKeyboards;
import com.shopbot.telegram.keyboard.ShopKeyboards;

/** Lịch sử mua: 10 đơn gần nhất, bấm vào để xem lại tài khoản. */
@Component
public class PurchaseHistoryHandler implements CallbackHandler {

    private final TelegramSender sender;
    private final MessageService msg;
    private final PaymentKeyboards keyboards;
    private final ShopKeyboards shopKeyboards;
    private final OrderService orders;
    private final OrderDeliveryMessenger delivery;

    public PurchaseHistoryHandler(TelegramSender sender, MessageService msg, PaymentKeyboards keyboards,
                                  ShopKeyboards shopKeyboards, OrderService orders, OrderDeliveryMessenger delivery) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
        this.shopKeyboards = shopKeyboards;
        this.orders = orders;
        this.delivery = delivery;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.HISTORY, CallbackData.HISTORY_ORDER);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        if (CallbackData.HISTORY_ORDER.equals(parts[0])) {
            delivery.deliver(ctx.chatId(), ctx.lang(), orders.paidOrderOf(ctx.userId(), parts[1]));
            return;
        }
        List<ShopOrder> recent = orders.recentPaidOrders(ctx.userId());
        if (recent.isEmpty()) {
            sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "history.empty"),
                    shopKeyboards.backHome(ctx.lang()));
            return;
        }
        sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "history.title"),
                keyboards.history(ctx.lang(), recent));
    }
}
