package com.shopbot.telegram;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.Product;
import com.shopbot.domain.UserAccount;
import com.shopbot.service.MessageService;
import com.shopbot.service.StockService;
import com.shopbot.service.UserService;
import com.shopbot.telegram.keyboard.Buttons;
import com.shopbot.util.Formats;

/**
 * Thông báo "hàng về" tới group/channel (NOTIFY_CHAT_ID, tiếng Việt) và DM từng user theo ngôn ngữ của họ.
 * Chạy nền, giãn cách ~20 tin/giây để không vượt giới hạn Telegram.
 */
@Component
public class RestockNotifier {

    private static final Logger log = LoggerFactory.getLogger(RestockNotifier.class);
    private static final long DELAY_MS = 50;
    private static final int MAX_TEXT_LENGTH = 4000;

    private final TelegramSender sender;
    private final MessageService msg;
    private final UserService users;
    private final ShopProperties props;

    public RestockNotifier(TelegramSender sender, MessageService msg, UserService users, ShopProperties props) {
        this.sender = sender;
        this.msg = msg;
        this.users = users;
        this.props = props;
    }

    @Async
    public void announce(StockService.AddResult result) {
        Product p = result.product();
        if (result.added() <= 0 || !p.isActive()) {
            return;
        }
        Map<String, String> textByLang = new HashMap<>();
        Map<String, InlineKeyboardMarkup> keyboardByLang = new HashMap<>();
        String notifyChat = props.bot().notifyChatId();
        if (notifyChat != null && !notifyChat.isBlank()) {
            String lang = MessageService.DEFAULT_LANG;
            sender.send(notifyChat.trim(), textByLang.computeIfAbsent(lang, l -> text(l, result)),
                    keyboardByLang.computeIfAbsent(lang, l -> keyboard(l, p)));
        }
        int sent = 0;
        for (UserAccount u : users.all()) {
            String lang = MessageService.normalize(u.getLang());
            Integer id = sender.send(u.getUserId(), textByLang.computeIfAbsent(lang, l -> text(l, result)),
                    keyboardByLang.computeIfAbsent(lang, l -> keyboard(l, p)));
            if (id != null) {
                sent++;
            }
            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.info("Restock announce product {}: sent to {} users", p.getId(), sent);
    }

    private String text(String lang, StockService.AddResult r) {
        Product p = r.product();
        String text = msg.t(lang, "restock.text", Formats.html(p.label()), r.added(), r.currentStock(),
                Formats.price(p.getPrice()), Formats.usdt(p.getPrice(), props.payment().usdtRate()));
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            String withDesc = text + msg.t(lang, "restock.description", p.getDescription());
            if (withDesc.length() <= MAX_TEXT_LENGTH) {
                return withDesc;
            }
        }
        return text;
    }

    private InlineKeyboardMarkup keyboard(String lang, Product p) {
        return Buttons.markup(Buttons.row(Buttons.url(msg.t(lang, "btn.buy_now"), props.deepLink("p_" + p.getId()))));
    }
}
