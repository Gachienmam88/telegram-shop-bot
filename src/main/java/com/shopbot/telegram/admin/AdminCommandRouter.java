package com.shopbot.telegram.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import com.shopbot.service.ShopException;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.util.Formats;

/** Phân lệnh admin tới đúng nhóm lệnh. Chỉ được gọi khi user nằm trong ADMIN_IDS. */
@Component
public class AdminCommandRouter {

    private static final Logger log = LoggerFactory.getLogger(AdminCommandRouter.class);

    private final Map<String, AdminCommandSet.AdminCommand> commands = new HashMap<>();
    private final TelegramSender sender;

    public AdminCommandRouter(List<AdminCommandSet> sets, TelegramSender sender) {
        sets.forEach(s -> commands.putAll(s.commands()));
        this.sender = sender;
    }

    public boolean handles(String command) {
        return commands.containsKey(command);
    }

    public void handle(BotContext ctx, String command, String fullText, Message message) {
        int newline = fullText.indexOf('\n');
        String firstLine = newline < 0 ? fullText : fullText.substring(0, newline);
        String body = newline < 0 ? "" : fullText.substring(newline + 1);
        String[] tokens = firstLine.trim().split("\\s+", 2);
        String args = tokens.length > 1 ? tokens[1].trim() : "";
        try {
            commands.get(command).run(new AdminRequest(ctx, args, body, message));
        } catch (IllegalArgumentException | ShopException e) {
            sender.send(ctx.chatId(), "❌ " + Formats.html(e.getMessage()), null);
        } catch (Exception e) {
            log.error("Admin command {} failed", command, e);
            sender.send(ctx.chatId(), "❌ Lỗi: " + Formats.html(String.valueOf(e.getMessage())), null);
        }
    }
}
