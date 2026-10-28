package com.shopbot.telegram.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.shopbot.service.ConversationService;
import com.shopbot.service.MessageService;
import com.shopbot.service.UserService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.AccountKeyboards;
import com.shopbot.telegram.keyboard.ShopKeyboards;

/**
 * /start (kèm deep link p_&lt;productId&gt; hoặc ref_&lt;userId&gt;) và chọn ngôn ngữ.
 */
@Component
public class StartHandler implements CallbackHandler {

    private final TelegramSender sender;
    private final MessageService msg;
    private final ShopKeyboards keyboards;
    private final AccountKeyboards accountKeyboards;
    private final UserService users;
    private final ConversationService conversations;
    private final HomeHandler home;
    private final CatalogHandler catalog;

    public StartHandler(TelegramSender sender, MessageService msg, ShopKeyboards keyboards,
                        AccountKeyboards accountKeyboards, UserService users, ConversationService conversations,
                        HomeHandler home, CatalogHandler catalog) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
        this.accountKeyboards = accountKeyboards;
        this.users = users;
        this.conversations = conversations;
        this.home = home;
        this.catalog = catalog;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.LANG, CallbackData.LANG_MENU, CallbackData.MODE_MENU, CallbackData.MODE_BUY);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        switch (parts[0]) {
            case CallbackData.LANG -> {
                if (parts.length > 1) {
                    String lang = MessageService.normalize(parts[1]);
                    users.setLanguage(ctx.userId(), lang);
                    showModeMenu(ctx.withLang(lang));
                }
            }
            case CallbackData.MODE_MENU -> showModeMenu(ctx);
            case CallbackData.MODE_BUY -> home.showHome(ctx);
            default -> sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "lang.prompt"), keyboards.language());
        }
    }

    /** Màn hình 2 lựa chọn: mua acc AI / bán acc TikTok. */
    public void showModeMenu(BotContext ctx) {
        conversations.clear(ctx.userId());
        sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "mode.text"), accountKeyboards.modeMenu(ctx.lang()));
    }

    public void onStart(BotContext ctx, String payload) {
        conversations.clear(ctx.userId());
        if (payload.startsWith("ref_") && ctx.newUser()) {
            parseLong(payload.substring(4)).ifPresent(ref -> users.assignReferrer(ctx.userId(), ref));
        }
        if (payload.startsWith("p_")) {
            var productId = parseLong(payload.substring(2));
            if (productId.isPresent()) {
                catalog.showProduct(ctx, productId.get());
                return;
            }
        }
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "lang.prompt"), keyboards.language());
    }

    private static java.util.Optional<Long> parseLong(String s) {
        try {
            return java.util.Optional.of(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
