package com.shopbot.telegram.handler;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.ConversationStep;
import com.shopbot.domain.Product;
import com.shopbot.domain.ShopOrder;
import com.shopbot.service.CatalogService;
import com.shopbot.service.ConversationService;
import com.shopbot.service.MessageService;
import com.shopbot.service.OrderService;
import com.shopbot.service.VietQrService;
import com.shopbot.service.WalletService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.OrderDeliveryMessenger;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.PaymentKeyboards;
import com.shopbot.telegram.keyboard.ShopKeyboards;
import com.shopbot.util.Formats;
import com.shopbot.util.InputValidators;

/** Nhập số lượng → chọn thanh toán ví / QR → giao hàng hoặc gửi QR; hủy đơn. */
@Component
public class CheckoutHandler implements CallbackHandler {

    private final TelegramSender sender;
    private final MessageService msg;
    private final PaymentKeyboards keyboards;
    private final ShopKeyboards shopKeyboards;
    private final CatalogService catalog;
    private final OrderService orders;
    private final WalletService wallet;
    private final ConversationService conversations;
    private final OrderDeliveryMessenger delivery;
    private final VietQrService vietQr;
    private final HomeHandler home;
    private final ShopProperties props;

    public CheckoutHandler(TelegramSender sender, MessageService msg, PaymentKeyboards keyboards,
                           ShopKeyboards shopKeyboards, CatalogService catalog, OrderService orders,
                           WalletService wallet, ConversationService conversations, OrderDeliveryMessenger delivery,
                           VietQrService vietQr, HomeHandler home, ShopProperties props) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
        this.shopKeyboards = shopKeyboards;
        this.catalog = catalog;
        this.orders = orders;
        this.wallet = wallet;
        this.conversations = conversations;
        this.delivery = delivery;
        this.vietQr = vietQr;
        this.home = home;
        this.props = props;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.PAY_WALLET, CallbackData.PAY_BANK, CallbackData.CHECKOUT_CANCEL,
                CallbackData.ORDER_CANCEL);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        switch (parts[0]) {
            case CallbackData.PAY_WALLET -> payByWallet(ctx);
            case CallbackData.PAY_BANK -> payByBank(ctx);
            case CallbackData.ORDER_CANCEL -> cancelOrder(ctx, parts[1]);
            default -> {
                sender.delete(ctx.chatId(), ctx.messageId());
                home.showHome(ctx.asNewMessage());
            }
        }
    }

    /** Khách gõ số lượng khi đang ở bước AWAIT_QUANTITY. */
    public void onQuantity(BotContext ctx, String productIdData, String text) {
        long productId = Long.parseLong(productIdData);
        int stock = catalog.availableStock(productId);
        if (stock == 0) {
            conversations.clear(ctx.userId());
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "product.out_of_stock"), shopKeyboards.backHome(ctx.lang()));
            return;
        }
        InputValidators.Result r = InputValidators.quantity(text, stock);
        if (!r.ok()) {
            String key = r.status() == InputValidators.Status.OUT_OF_RANGE ? "qty.exceed" : "qty.invalid";
            sender.send(ctx.chatId(), msg.t(ctx.lang(), key, stock), null);
            return;
        }
        int quantity = (int) r.value();
        Product p = catalog.activeProduct(productId);
        long total = p.getPrice() * quantity;
        long balance = wallet.balance(ctx.userId());
        conversations.set(ctx.userId(), ConversationStep.CHECKOUT, productId + ":" + quantity);
        String summary = msg.t(ctx.lang(), "checkout.summary", Formats.html(p.label()), quantity,
                Formats.price(total), Formats.money(balance));
        if (balance < total) {
            summary += msg.t(ctx.lang(), "checkout.insufficient_hint");
        }
        sender.send(ctx.chatId(), summary, keyboards.checkout(ctx.lang(), balance >= total));
    }

    private void payByWallet(BotContext ctx) {
        Optional<long[]> sel = consumeCheckout(ctx);
        if (sel.isEmpty()) {
            return;
        }
        var paid = orders.payWithWallet(ctx.userId(), ctx.chatId(), sel.get()[0], (int) sel.get()[1]);
        sender.delete(ctx.chatId(), ctx.messageId());
        delivery.deliver(ctx.chatId(), ctx.lang(), paid);
    }

    private void payByBank(BotContext ctx) {
        Optional<long[]> sel = consumeCheckout(ctx);
        if (sel.isEmpty()) {
            return;
        }
        ShopOrder o = orders.createBankOrder(ctx.userId(), ctx.chatId(), sel.get()[0], (int) sel.get()[1]);
        sender.delete(ctx.chatId(), ctx.messageId());
        ShopProperties.Bank bank = props.bank();
        String caption = msg.t(ctx.lang(), "order.bank_caption", o.getCode(), Formats.html(o.getProductName()),
                o.getQuantity(), Formats.time(o.getExpiresAt(), props.zone()), Formats.html(bank.displayName()),
                Formats.html(bank.accountName()), bank.accountNumber(), Formats.money(o.getAmount()), o.getPayRef(),
                props.timeoutMinutes());
        Integer messageId = sender.sendPhoto(ctx.chatId(), vietQr.qrImageUrl(o.getAmount(), o.getPayRef()), caption,
                keyboards.bankOrder(ctx.lang(), o.getCode()));
        if (messageId != null && !orders.attachPaymentMessage(o.getCode(), messageId)) {
            // Webhook đã xử lý xong trước khi kịp lưu id tin → xóa QR thừa.
            sender.delete(ctx.chatId(), messageId);
        }
    }

    private void cancelOrder(BotContext ctx, String code) {
        Optional<ShopOrder> cancelled = orders.cancelPending(code, ctx.userId());
        if (cancelled.isEmpty()) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "order.cannot_cancel"), shopKeyboards.backHome(ctx.lang()));
            return;
        }
        ShopOrder o = cancelled.get();
        sender.delete(ctx.chatId(), o.getPaymentMessageId());
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "order.cancelled_by_user", o.getCode(),
                Formats.html(o.getProductName())), shopKeyboards.backHome(ctx.lang()));
    }

    /** Lấy (productId, quantity) đã chọn; dùng 1 lần để bấm nút 2 lần không tạo 2 đơn. */
    Optional<long[]> consumeCheckout(BotContext ctx) {
        Optional<String> data = conversations.consume(ctx.userId(), ConversationStep.CHECKOUT);
        if (data.isEmpty()) {
            sender.delete(ctx.chatId(), ctx.messageId());
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "checkout.expired"), shopKeyboards.backHome(ctx.lang()));
            return Optional.empty();
        }
        String[] p = data.get().split(":");
        return Optional.of(new long[] {Long.parseLong(p[0]), Long.parseLong(p[1])});
    }
}
