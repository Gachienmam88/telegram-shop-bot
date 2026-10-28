package com.shopbot.telegram;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.shopbot.domain.ShopOrder;
import com.shopbot.service.MessageService;
import com.shopbot.service.PaidOrder;
import com.shopbot.telegram.keyboard.PaymentKeyboards;
import com.shopbot.util.Formats;

/** Gửi tài khoản đã mua cho khách: ít thì gửi text, nhiều thì kèm file .txt. */
@Component
public class OrderDeliveryMessenger {

    private static final int MAX_INLINE_ACCOUNTS = 20;
    private static final int MAX_TEXT_LENGTH = 3800;

    private final TelegramSender sender;
    private final MessageService msg;
    private final PaymentKeyboards keyboards;

    public OrderDeliveryMessenger(TelegramSender sender, MessageService msg, PaymentKeyboards keyboards) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
    }

    public void deliver(long chatId, String lang, PaidOrder paid) {
        ShopOrder o = paid.order();
        String accounts = paid.accounts().stream()
                .map(a -> "<code>" + Formats.html(a) + "</code>")
                .collect(Collectors.joining("\n"));
        String text = msg.t(lang, "order.delivered", o.getCode(), Formats.html(o.getProductName()), o.getQuantity(),
                Formats.price(o.getAmount()), accounts);
        if (paid.accounts().size() <= MAX_INLINE_ACCOUNTS && text.length() <= MAX_TEXT_LENGTH) {
            sender.send(chatId, text, keyboards.afterPurchase(lang));
            return;
        }
        sender.send(chatId, msg.t(lang, "order.delivered_file", o.getCode(), Formats.html(o.getProductName()),
                o.getQuantity(), Formats.price(o.getAmount())), keyboards.afterPurchase(lang));
        byte[] file = String.join("\n", paid.accounts()).getBytes(StandardCharsets.UTF_8);
        sender.sendDocument(chatId, o.getCode() + ".txt", file, o.getCode());
    }
}
