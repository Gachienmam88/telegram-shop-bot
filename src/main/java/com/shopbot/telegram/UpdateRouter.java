package com.shopbot.telegram;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.ConversationState;
import com.shopbot.service.ConversationService;
import com.shopbot.service.MessageService;
import com.shopbot.service.ShopException;
import com.shopbot.service.UserService;
import com.shopbot.telegram.admin.AdminCommandRouter;
import com.shopbot.telegram.handler.AccountSaleHandler;
import com.shopbot.telegram.handler.CheckoutHandler;
import com.shopbot.telegram.handler.CryptoPaymentHandler;
import com.shopbot.telegram.handler.HomeHandler;
import com.shopbot.telegram.handler.StartHandler;
import com.shopbot.telegram.handler.WalletHandler;
import com.shopbot.telegram.handler.WithdrawHandler;
import com.shopbot.telegram.keyboard.ShopKeyboards;

/**
 * Phân luồng mỗi update: nút inline → CallbackHandler, lệnh /... → start/admin,
 * text → theo bước hội thoại.
 */
@Component
public class UpdateRouter {

    private static final Logger log = LoggerFactory.getLogger(UpdateRouter.class);

    /** Lệnh trong menu "/" → mở màn hình giống nút inline tương ứng. */
    private static final Map<String, String> COMMAND_SCREENS = Map.ofEntries(
            Map.entry("/home", CallbackData.HOME),
            Map.entry("/products", CallbackData.CATEGORIES),
            Map.entry("/orders", CallbackData.HISTORY),
            Map.entry("/wallet", CallbackData.WALLET),
            Map.entry("/topup", CallbackData.TOPUP),
            Map.entry("/affiliate", CallbackData.AFFILIATE),
            Map.entry("/support", CallbackData.SUPPORT),
            Map.entry("/guide", CallbackData.GUIDE),
            Map.entry("/language", CallbackData.LANG_MENU),
            Map.entry("/sell", CallbackData.SELL_MENU),
            Map.entry("/withdraw", CallbackData.WITHDRAW));

    private final Map<String, CallbackHandler> callbackRoutes = new HashMap<>();
    private final UserService users;
    private final ConversationService conversations;
    private final MessageService msg;
    private final TelegramSender sender;
    private final ShopKeyboards keyboards;
    private final ShopProperties props;
    private final StartHandler start;
    private final HomeHandler home;
    private final CheckoutHandler checkout;
    private final WalletHandler wallet;
    private final CryptoPaymentHandler crypto;
    private final AccountSaleHandler accountSale;
    private final WithdrawHandler withdraw;
    private final AdminCommandRouter admin;

    public UpdateRouter(List<CallbackHandler> handlers, UserService users, ConversationService conversations,
            MessageService msg, TelegramSender sender, ShopKeyboards keyboards, ShopProperties props,
            StartHandler start, HomeHandler home, CheckoutHandler checkout, WalletHandler wallet,
            CryptoPaymentHandler crypto, AccountSaleHandler accountSale, WithdrawHandler withdraw,
            AdminCommandRouter admin) {
        handlers.forEach(h -> h.actions().forEach(a -> callbackRoutes.put(a, h)));
        this.users = users;
        this.conversations = conversations;
        this.msg = msg;
        this.sender = sender;
        this.keyboards = keyboards;
        this.props = props;
        this.start = start;
        this.home = home;
        this.checkout = checkout;
        this.wallet = wallet;
        this.crypto = crypto;
        this.accountSale = accountSale;
        this.withdraw = withdraw;
        this.admin = admin;
    }

    public void route(Update update) {
        if (update.hasCallbackQuery()) {
            onCallback(update.getCallbackQuery());
        } else if (update.hasMessage()) {
            onMessage(update.getMessage());
        }
    }

    private void onCallback(CallbackQuery cq) {
        sender.answerCallback(cq.getId());
        MaybeInaccessibleMessage m = cq.getMessage();
        if (m == null || cq.getData() == null) {
            return;
        }
        String[] parts = cq.getData().split(":", 2);
        CallbackHandler handler = callbackRoutes.get(parts[0]);
        if (handler == null) {
            return;
        }
        BotContext ctx = context(cq.getFrom(), m.getChatId(), m.getMessageId());
        run(ctx, () -> handler.handle(ctx, parts));
    }

    private void onMessage(Message m) {
        // Chỉ phục vụ chat riêng; trong group bot chỉ đăng thông báo hàng về.
        if (!m.isUserMessage() || m.getFrom() == null) {
            return;
        }
        BotContext ctx = context(m.getFrom(), m.getChatId(), null);
        String text = m.hasText() ? m.getText() : Optional.ofNullable(m.getCaption()).orElse("");
        run(ctx, () -> {
            if (text.startsWith("/")) {
                onCommand(ctx, text, m);
            } else if (m.hasText()) {
                onPlainText(ctx, text);
            }
        });
    }

    private void onCommand(BotContext ctx, String text, Message m) {
        String firstLine = text.lines().findFirst().orElse("");
        String[] tokens = firstLine.trim().split("\\s+", 2);
        String command = tokens[0].toLowerCase(Locale.ROOT).replaceFirst("@.*$", "");
        if ("/start".equals(command)) {
            start.onStart(ctx, tokens.length > 1 ? tokens[1].trim() : "");
        } else if (ctx.admin() && admin.handles(command)) {
            admin.handle(ctx, command, text, m);
        } else if (COMMAND_SCREENS.containsKey(command)) {
            String action = COMMAND_SCREENS.get(command);
            callbackRoutes.get(action).handle(ctx, new String[] { action });
        } else {
            home.showHome(ctx);
        }
    }

    private void onPlainText(BotContext ctx, String text) {
        Optional<ConversationState> state = conversations.current(ctx.userId());
        if (state.isEmpty()) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "common.use_menu"), keyboards.home(ctx.lang()));
            return;
        }
        switch (state.get().getState()) {
            case AWAIT_QUANTITY -> checkout.onQuantity(ctx, state.get().getData(), text);
            case AWAIT_TOPUP_AMOUNT -> wallet.onTopupAmount(ctx, text);
            case AWAIT_CRYPTO_TOPUP_AMOUNT -> crypto.onTopupAmount(ctx, text);
            case AWAIT_SALE_CONTACT -> accountSale.onContact(ctx, text);
            case AWAIT_SALE_PASSWORD -> accountSale.onPassword(ctx, state.get().getData(), text);
            case AWAIT_SALE_PRICE -> accountSale.onPrice(ctx, state.get().getData(), text);
            case AWAIT_WITHDRAW_AMOUNT -> withdraw.onAmount(ctx, text);
            case AWAIT_BANK_INFO -> withdraw.onBankInfo(ctx, state.get().getData(), text);
            default -> sender.send(ctx.chatId(), msg.t(ctx.lang(), "common.use_menu"), keyboards.home(ctx.lang()));
        }
    }

    private BotContext context(User from, long chatId, Integer messageId) {
        UserService.Registration reg = users.register(from.getId(), from.getUserName());
        return new BotContext(from.getId(), chatId, MessageService.normalize(reg.user().getLang()), messageId,
                props.isAdmin(from.getId()), reg.created());
    }

    /**
     * Lỗi nghiệp vụ → báo khách bằng câu i18n; lỗi khác → log + câu chung chung.
     */
    private void run(BotContext ctx, Runnable action) {
        try {
            action.run();
        } catch (ShopException e) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), e.messageKey(), e.args()), keyboards.backHome(ctx.lang()));
        } catch (Exception e) {
            log.error("Error handling update from user {}", ctx.userId(), e);
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "common.error"), keyboards.backHome(ctx.lang()));
        }
    }
}
