package com.shopbot.telegram.admin;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.shopbot.telegram.ChatWiper;
import com.shopbot.telegram.TelegramSender;

/** Lệnh admin dọn chat: /wipechats XACNHAN [số tin] → xóa tin nhắn với mọi khách (không đụng chat admin); /wipechats STOP → dừng. */
@Component
public class AdminChatCommands implements AdminCommandSet {

    static final String CONFIRM_WORD = "XACNHAN";
    static final String STOP_WORD = "STOP";
    private static final int DEFAULT_DEPTH = 1000;
    private static final int MAX_DEPTH = 10_000;

    private final ChatWiper wiper;
    private final TelegramSender sender;

    public AdminChatCommands(ChatWiper wiper, TelegramSender sender) {
        this.wiper = wiper;
        this.sender = sender;
    }

    @Override
    public Map<String, AdminCommand> commands() {
        return Map.of("/wipechats", this::wipeChats);
    }

    private void wipeChats(AdminRequest r) {
        String[] w = r.words();
        long chatId = r.ctx().chatId();
        if (w.length > 0 && STOP_WORD.equalsIgnoreCase(w[0])) {
            sender.send(chatId, wiper.cancel() ? "⛔ Đang dừng xóa chat (xong khách đang xử lý dở sẽ dừng)..."
                    : "ℹ️ Không có lượt xóa chat nào đang chạy.", null);
            return;
        }
        if (w.length == 0 || !CONFIRM_WORD.equals(w[0])) {
            sender.send(chatId, "⚠️ <b>Xóa TOÀN BỘ tin nhắn với mọi khách</b> (cả tin bot lẫn tin khách, không hoàn tác)."
                    + "\n• Không xóa chat của admin, không thông báo cho khách."
                    + "\n• Telegram chỉ cho xóa tin gửi trong <b>48 giờ</b> gần nhất."
                    + "\n• Mỗi chat quét " + DEFAULT_DEPTH + " tin gần nhất (đổi bằng số ở cuối, tối đa " + MAX_DEPTH + ")."
                    + "\n\nGõ để chạy: <code>/wipechats " + CONFIRM_WORD + "</code>"
                    + "\nhoặc: <code>/wipechats " + CONFIRM_WORD + " 3000</code>"
                    + "\nDừng giữa chừng: <code>/wipechats " + STOP_WORD + "</code>", null);
            return;
        }
        int depth = w.length > 1 ? (int) AdminRequest.parseLong(w[1], "số tin") : DEFAULT_DEPTH;
        if (depth < 1 || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Số tin phải từ 1 đến " + MAX_DEPTH);
        }
        if (!wiper.tryStart()) {
            sender.send(chatId, "⏳ Đang có lượt xóa chat khác chạy, đợi xong rồi thử lại.", null);
            return;
        }
        Integer statusId = sender.send(chatId, "🧹 <b>Đang xóa chat...</b>\nTin này tự cập nhật tiến độ mỗi 5 giây."
                + "\nDừng: <code>/wipechats " + STOP_WORD + "</code>", null);
        wiper.wipeAllAsync(chatId, statusId, depth);
    }
}
