package com.shopbot.telegram.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.shopbot.config.ShopProperties;
import com.shopbot.service.ConversationService;
import com.shopbot.service.MessageService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.ShopKeyboards;
import com.shopbot.util.Formats;

/** Trang chủ + các trang chỉ hiển thị nội dung: Hướng dẫn, Bảo hành, Hỗ trợ. */
@Component
public class HomeHandler implements CallbackHandler {

    private final TelegramSender sender;
    private final MessageService msg;
    private final ShopKeyboards keyboards;
    private final ConversationService conversations;
    private final ShopProperties props;

    public HomeHandler(TelegramSender sender, MessageService msg, ShopKeyboards keyboards,
                       ConversationService conversations, ShopProperties props) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
        this.conversations = conversations;
        this.props = props;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.HOME, CallbackData.GUIDE, CallbackData.WARRANTY, CallbackData.SUPPORT,
                CallbackData.NOOP);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        switch (parts[0]) {
            case CallbackData.HOME -> showHome(ctx);
            case CallbackData.GUIDE -> info(ctx, msg.t(ctx.lang(), "guide.text", props.timeoutMinutes()));
            case CallbackData.WARRANTY -> info(ctx, msg.t(ctx.lang(), "warranty.text"));
            case CallbackData.SUPPORT -> info(ctx, msg.t(ctx.lang(), "support.text", supportHandle()));
            default -> {
                // NOOP: nút tiêu đề, không làm gì.
            }
        }
    }

    /** Về trang chủ: hủy mọi bước đang chờ nhập. */
    public void showHome(BotContext ctx) {
        conversations.clear(ctx.userId());
        sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "home.text", Formats.html(props.name())),
                keyboards.home(ctx.lang()));
    }

    private void info(BotContext ctx, String text) {
        sender.show(ctx.chatId(), ctx.messageId(), text, keyboards.backHome(ctx.lang()));
    }

    private String supportHandle() {
        String u = props.bot().supportUsername();
        return u == null || u.isBlank() ? "admin" : "@" + u.replace("@", "");
    }
}
