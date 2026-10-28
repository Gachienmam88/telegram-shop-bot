package com.shopbot.telegram;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeChat;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import com.shopbot.config.ShopProperties;
import com.shopbot.service.MessageService;

/**
 * Đăng ký menu lệnh "/" khi bot khởi động:
 * - Khách: lệnh mua hàng, mô tả theo ngôn ngữ Telegram của khách (vi/en/th/zh/ko).
 * - Admin (ADMIN_IDS): thêm các lệnh quản trị — chỉ hiện trong chat của admin.
 */
@Component
@ConditionalOnProperty(prefix = "shop.bot", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
public class BotCommandsRegistrar {

    private static final Logger log = LoggerFactory.getLogger(BotCommandsRegistrar.class);

    /** Lệnh khách → key i18n mô tả. Thứ tự = thứ tự hiển thị. */
    private static final List<String> CUSTOMER_COMMANDS =
            List.of("start", "home", "products", "orders", "wallet", "topup", "withdraw", "sell", "affiliate",
                    "support", "language");

    private static final Map<String, String> ADMIN_COMMANDS = adminCommands();

    private final TelegramClient client;
    private final MessageService msg;
    private final ShopProperties props;

    public BotCommandsRegistrar(TelegramClient client, MessageService msg, ShopProperties props) {
        this.client = client;
        this.msg = msg;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        try {
            for (String lang : MessageService.SUPPORTED_LANGS) {
                client.execute(SetMyCommands.builder().commands(customerCommands(lang))
                        .scope(new BotCommandScopeDefault()).languageCode(lang).build());
            }
            // Không có languageCode: áp dụng cho khách dùng ngôn ngữ khác → tiếng Việt.
            client.execute(SetMyCommands.builder().commands(customerCommands(MessageService.DEFAULT_LANG))
                    .scope(new BotCommandScopeDefault()).build());

            List<BotCommand> adminMenu = new ArrayList<>(customerCommands(MessageService.DEFAULT_LANG));
            ADMIN_COMMANDS.forEach((cmd, desc) -> adminMenu.add(new BotCommand(cmd, desc)));
            for (Long adminId : props.bot().adminIds()) {
                client.execute(SetMyCommands.builder().commands(adminMenu)
                        .scope(BotCommandScopeChat.builder().chatId(adminId).build()).build());
            }
            log.info("Registered bot command menu ({} admins)", props.bot().adminIds().size());
        } catch (TelegramApiException e) {
            log.warn("Cannot register bot commands: {}", e.getMessage());
        }
    }

    private List<BotCommand> customerCommands(String lang) {
        return CUSTOMER_COMMANDS.stream().map(c -> new BotCommand(c, msg.t(lang, "cmd." + c))).toList();
    }

    private static Map<String, String> adminCommands() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("admin", "🛠 Danh sách lệnh admin");
        m.put("cats", "Xem danh mục");
        m.put("plist", "Xem sản phẩm kèm id, giá, tồn kho");
        m.put("stats", "Thống kê doanh thu");
        m.put("addstock", "Nạp kho: /addstock id + mỗi dòng 1 acc");
        m.put("delstock", "Xóa mềm kho: /delstock id [+ các dòng]");
        m.put("confirm", "Xác nhận thanh toán tay: /confirm DHxxxx");
        m.put("addbal", "Cộng/trừ ví: /addbal user_id số_tiền");
        m.put("setaff", "Hoa hồng affiliate: /setaff %");
        m.put("wipechats", "Xóa chat với mọi khách (tin dưới 48h)");
        return m;
    }
}
