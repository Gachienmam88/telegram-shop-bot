package com.shopbot.telegram.handler;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.ConversationStep;
import com.shopbot.domain.CryptoInvoice;
import com.shopbot.domain.CryptoNetwork;
import com.shopbot.service.ConversationService;
import com.shopbot.service.CryptoInvoiceService;
import com.shopbot.service.CryptoWallets;
import com.shopbot.service.MessageService;
import com.shopbot.service.OrderService;
import com.shopbot.service.ShopException;
import com.shopbot.service.TopupService;
import com.shopbot.service.crypto.CryptoCheckoutService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.PaymentKeyboards;
import com.shopbot.telegram.keyboard.ShopKeyboards;
import com.shopbot.util.Formats;

/**
 * Thanh toán USDT: mua hàng (checkout → chọn mạng → invoice) và nạp ví (nhập số USDT → chọn mạng → invoice).
 * Tiền đến được CryptoDepositScheduler phát hiện và xử lý tự động.
 */
@Component
public class CryptoPaymentHandler implements CallbackHandler {

    private final TelegramSender sender;
    private final MessageService msg;
    private final PaymentKeyboards keyboards;
    private final ShopKeyboards shopKeyboards;
    private final ConversationService conversations;
    private final CryptoCheckoutService checkoutService;
    private final CryptoInvoiceService invoices;
    private final CryptoWallets wallets;
    private final OrderService orders;
    private final TopupService topups;
    private final CheckoutHandler checkout;
    private final ShopProperties props;

    public CryptoPaymentHandler(TelegramSender sender, MessageService msg, PaymentKeyboards keyboards,
                                ShopKeyboards shopKeyboards, ConversationService conversations,
                                CryptoCheckoutService checkoutService, CryptoInvoiceService invoices,
                                CryptoWallets wallets, OrderService orders, TopupService topups,
                                CheckoutHandler checkout, ShopProperties props) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
        this.shopKeyboards = shopKeyboards;
        this.conversations = conversations;
        this.checkoutService = checkoutService;
        this.invoices = invoices;
        this.wallets = wallets;
        this.orders = orders;
        this.topups = topups;
        this.checkout = checkout;
        this.props = props;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.PAY_CRYPTO, CallbackData.PAY_CRYPTO_NETWORK, CallbackData.TOPUP_CRYPTO,
                CallbackData.TOPUP_CRYPTO_NETWORK);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        if (!wallets.anyEnabled()) {
            throw new ShopException("crypto.network_disabled");
        }
        switch (parts[0]) {
            case CallbackData.PAY_CRYPTO -> sender.show(ctx.chatId(), ctx.messageId(),
                    msg.t(ctx.lang(), "crypto.choose_network"),
                    keyboards.cryptoNetworks(ctx.lang(), CallbackData.PAY_CRYPTO_NETWORK));
            case CallbackData.PAY_CRYPTO_NETWORK -> payOrder(ctx, network(parts));
            case CallbackData.TOPUP_CRYPTO -> askTopupAmount(ctx);
            default -> topup(ctx, network(parts));
        }
    }

    /** Khách gõ số USDT muốn nạp (bước AWAIT_CRYPTO_TOPUP_AMOUNT). */
    public void onTopupAmount(BotContext ctx, String text) {
        BigDecimal min = minTopup();
        BigDecimal max = invoices.toUsdt(props.payment().topupMax());
        Optional<BigDecimal> usdt = parseUsdt(text).filter(v -> v.compareTo(min) >= 0 && v.compareTo(max) <= 0);
        if (usdt.isEmpty()) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "crypto.topup_invalid", min.toPlainString(), max.toPlainString()),
                    shopKeyboards.backHome(ctx.lang()));
            return;
        }
        conversations.set(ctx.userId(), ConversationStep.CRYPTO_TOPUP, usdt.get().toPlainString());
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "crypto.choose_network"),
                keyboards.cryptoNetworks(ctx.lang(), CallbackData.TOPUP_CRYPTO_NETWORK));
    }

    private void payOrder(BotContext ctx, CryptoNetwork network) {
        Optional<long[]> sel = checkout.consumeCheckout(ctx);
        if (sel.isEmpty()) {
            return;
        }
        long productId = sel.get()[0];
        int quantity = (int) sel.get()[1];
        CryptoCheckoutService.OrderInvoice created;
        try {
            created = checkoutService.createOrder(ctx.userId(), ctx.chatId(), productId, quantity, network);
        } catch (ShopException e) {
            retryOtherNetwork(ctx, e, ConversationStep.CHECKOUT, productId + ":" + quantity,
                    CallbackData.PAY_CRYPTO_NETWORK);
            return;
        }
        var o = created.order();
        sender.delete(ctx.chatId(), ctx.messageId());
        String header = msg.t(ctx.lang(), "crypto.order_info", o.getCode(), Formats.html(o.getProductName()),
                o.getQuantity(), Formats.price(o.getAmount()));
        Integer messageId = sendInvoice(ctx, header, created.invoice(), keyboards.bankOrder(ctx.lang(), o.getCode()));
        if (messageId != null && !orders.attachPaymentMessage(o.getCode(), messageId)) {
            sender.delete(ctx.chatId(), messageId);
        }
    }

    private void askTopupAmount(BotContext ctx) {
        conversations.set(ctx.userId(), ConversationStep.AWAIT_CRYPTO_TOPUP_AMOUNT, null);
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "crypto.topup_prompt", minTopup().toPlainString(),
                invoices.toUsdt(props.payment().topupMax()).toPlainString(), Formats.money(invoices.rate().longValue())),
                shopKeyboards.backHome(ctx.lang()));
    }

    private void topup(BotContext ctx, CryptoNetwork network) {
        Optional<String> data = conversations.consume(ctx.userId(), ConversationStep.CRYPTO_TOPUP);
        if (data.isEmpty()) {
            sender.delete(ctx.chatId(), ctx.messageId());
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "checkout.expired"), shopKeyboards.backHome(ctx.lang()));
            return;
        }
        BigDecimal usdt = new BigDecimal(data.get());
        CryptoCheckoutService.TopupInvoice created;
        try {
            created = checkoutService.createTopup(ctx.userId(), ctx.chatId(), usdt, network);
        } catch (ShopException e) {
            retryOtherNetwork(ctx, e, ConversationStep.CRYPTO_TOPUP, data.get(), CallbackData.TOPUP_CRYPTO_NETWORK);
            return;
        }
        var t = created.topup();
        sender.delete(ctx.chatId(), ctx.messageId());
        String header = msg.t(ctx.lang(), "crypto.topup_info", usdtText(usdt), Formats.money(t.getAmount()),
                t.getCode());
        Integer messageId = sendInvoice(ctx, header, created.invoice(), keyboards.backWallet(ctx.lang()));
        if (messageId != null && !topups.attachPaymentMessage(t.getCode(), messageId)) {
            sender.delete(ctx.chatId(), messageId);
        }
    }

    /**
     * Số tiền đang bị khách khác giữ trên mạng này → giữ lại lựa chọn của khách và cho chọn mạng khác.
     * Lỗi khác ném tiếp cho UpdateRouter báo như bình thường.
     */
    private void retryOtherNetwork(BotContext ctx, ShopException e, ConversationStep step, String data,
                                   String networkAction) {
        if (!CryptoInvoiceService.AMOUNT_TAKEN.equals(e.messageKey())) {
            throw e;
        }
        conversations.set(ctx.userId(), step, data);
        sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), e.messageKey(), e.args()),
                keyboards.cryptoNetworks(ctx.lang(), networkAction));
    }

    /** Ảnh QR địa chỉ ví + thông tin chuyển làm caption (ảnh lỗi → TelegramSender tự gửi dạng text). */
    private Integer sendInvoice(BotContext ctx, String header, CryptoInvoice i, InlineKeyboardMarkup keyboard) {
        String address = wallets.address(i.getNetwork());
        String caption = msg.t(ctx.lang(), "crypto.invoice", header, i.getNetwork().displayName(),
                usdtText(i.getUsdtAmount()), address, Formats.time(i.getExpiresAt(), props.zone()));
        return sender.sendPhoto(ctx.chatId(), qrImageUrl(address), caption, keyboard);
    }

    /** QR chỉ chứa địa chỉ ví (định dạng mọi app ví đều đọc được); số tiền khách tự nhập. */
    static String qrImageUrl(String address) {
        return "https://api.qrserver.com/v1/create-qr-code/?size=400x400&margin=10&data="
                + URLEncoder.encode(address, StandardCharsets.UTF_8);
    }

    /** 10.0000 → "10", 4.4500 → "4.45" (DB lưu 4 chữ số thập phân). */
    static String usdtText(BigDecimal usdt) {
        return usdt.stripTrailingZeros().toPlainString();
    }

    /** Nạp tối thiểu: TOPUP_MIN quy đổi, nhưng không dưới 1 USDT. */
    private BigDecimal minTopup() {
        return invoices.toUsdt(props.payment().topupMin()).max(BigDecimal.ONE);
    }

    private static CryptoNetwork network(String[] parts) {
        try {
            return CryptoNetwork.valueOf(parts.length > 1 ? parts[1] : "");
        } catch (IllegalArgumentException e) {
            throw new ShopException("crypto.network_disabled");
        }
    }

    /** "10", "12.5", "12,5" → BigDecimal (tối đa 2 chữ số thập phân). */
    static Optional<BigDecimal> parseUsdt(String text) {
        String s = text == null ? "" : text.trim().replace(',', '.');
        if (!s.matches("\\d{1,9}(\\.\\d{1,2})?")) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(s));
    }
}
