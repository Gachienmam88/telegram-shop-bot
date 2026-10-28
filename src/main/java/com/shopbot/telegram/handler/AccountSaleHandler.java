package com.shopbot.telegram.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.shopbot.domain.AccountSubmission;
import com.shopbot.domain.ConversationStep;
import com.shopbot.service.AccountSaleService;
import com.shopbot.service.ConversationService;
import com.shopbot.service.MessageService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.PaymentOutcomeNotifier;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.AccountKeyboards;
import com.shopbot.telegram.keyboard.ShopKeyboards;
import com.shopbot.util.Formats;
import com.shopbot.util.InputValidators;

/**
 * Thu mua acc TikTok: giới thiệu yêu cầu → nhập email/SĐT → username → giá muốn
 * bán → gửi admin duyệt tay.
 * Không thu thập mật khẩu.
 */
@Component
public class AccountSaleHandler implements CallbackHandler {

    private static final long PRICE_MAX = 500_000_000L;

    private final TelegramSender sender;
    private final MessageService msg;
    private final ConversationService conversations;
    private final AccountSaleService sales;
    private final AccountKeyboards keyboards;
    private final ShopKeyboards shopKeyboards;
    private final PaymentOutcomeNotifier notifier;

    public AccountSaleHandler(TelegramSender sender, MessageService msg, ConversationService conversations,
            AccountSaleService sales, AccountKeyboards keyboards, ShopKeyboards shopKeyboards,
            PaymentOutcomeNotifier notifier) {
        this.sender = sender;
        this.msg = msg;
        this.conversations = conversations;
        this.sales = sales;
        this.keyboards = keyboards;
        this.shopKeyboards = shopKeyboards;
        this.notifier = notifier;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.SELL_MENU, CallbackData.SELL_START);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        if (CallbackData.SELL_START.equals(parts[0])) {
            conversations.set(ctx.userId(), ConversationStep.AWAIT_SALE_CONTACT, null);
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "sell.ask_contact"), shopKeyboards.backHome(ctx.lang()));
        } else {
            sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "sell.intro"),
                    keyboards.sellIntro(ctx.lang()));
        }
    }

    /** Bước 1: email hoặc số điện thoại. */
    public void onContact(BotContext ctx, String text) {
        String contact = text.trim();
        if (!InputValidators.isAccountContact(contact)) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "sell.invalid_contact"), shopKeyboards.backHome(ctx.lang()));
            return;
        }
        conversations.set(ctx.userId(), ConversationStep.AWAIT_SALE_PASSWORD, contact);
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "sell.ask_password"), shopKeyboards.backHome(ctx.lang()));
    }

    /** Bước 2: username tài khoản. */
    public void onPassword(BotContext ctx, String contact, String text) {
        String password = text.trim();
        if (!InputValidators.isPassword(password)) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "sell.invalid_password"), shopKeyboards.backHome(ctx.lang()));
            return;
        }
        conversations.set(ctx.userId(), ConversationStep.AWAIT_SALE_PRICE, contact + "\n" + password);
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "sell.ask_price"), shopKeyboards.backHome(ctx.lang()));
    }

    /** Bước 3: giá muốn bán → tạo hồ sơ, báo admin. */
    public void onPrice(BotContext ctx, String data, String text) {
        InputValidators.Result r = InputValidators.amount(text, 1, PRICE_MAX);
        if (!r.ok()) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "sell.invalid_price"), shopKeyboards.backHome(ctx.lang()));
            return;
        }
        String[] saved = data.split("\n", 2);
        String contact = saved[0];
        String password = saved.length > 1 ? saved[1] : "";
        conversations.clear(ctx.userId());
        AccountSubmission s = sales.create(ctx.userId(), ctx.chatId(), contact, password, r.value());
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "sell.submitted", s.getCode(), Formats.money(s.getAskingPrice())),
                shopKeyboards.backHome(ctx.lang()));
        notifier.notifyAdmins(msg.t("vi", "sell.admin_notify", s.getCode(), ctx.userId(), Formats.html(contact),
                Formats.html(password), Formats.money(s.getAskingPrice())), keyboards.saleReview(s.getCode()));
    }
}
