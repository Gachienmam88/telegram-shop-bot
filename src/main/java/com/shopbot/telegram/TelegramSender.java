package com.shopbot.telegram;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Bọc TelegramClient: mọi tin nhắn gửi đi dạng HTML, lỗi được log thay vì làm hỏng luồng xử lý.
 */
@Component
public class TelegramSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramSender.class);

    private final TelegramClient client;

    public TelegramSender(TelegramClient client) {
        this.client = client;
    }

    /** Gửi tin mới. Trả về message_id hoặc null nếu lỗi. */
    public Integer send(long chatId, String html, InlineKeyboardMarkup keyboard) {
        return send(String.valueOf(chatId), html, keyboard);
    }

    /** chatId dạng chuỗi để gửi được tới channel "@ten_kenh". */
    public Integer send(String chatId, String html, InlineKeyboardMarkup keyboard) {
        try {
            return sendOrThrow(chatId, html, keyboard);
        } catch (TelegramApiException e) {
            log.warn("send to {} failed: {}", chatId, e.getMessage());
            return null;
        }
    }

    public Integer sendOrThrow(String chatId, String html, InlineKeyboardMarkup keyboard) throws TelegramApiException {
        SendMessage m = SendMessage.builder().chatId(chatId).text(html).parseMode(ParseMode.HTML)
                .replyMarkup(keyboard).build();
        Message sent = client.execute(m);
        return sent == null ? null : sent.getMessageId();
    }

    /**
     * Hiển thị 1 màn hình: sửa tin đang có nút (messageId != null) cho gọn chat,
     * nếu không sửa được (vd tin ảnh QR) thì gửi tin mới.
     */
    public void show(long chatId, Integer messageId, String html, InlineKeyboardMarkup keyboard) {
        if (messageId == null) {
            send(chatId, html, keyboard);
            return;
        }
        try {
            client.execute(EditMessageText.builder().chatId(chatId).messageId(messageId).text(html)
                    .parseMode(ParseMode.HTML).replyMarkup(keyboard).build());
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                return;
            }
            send(chatId, html, keyboard);
        }
    }

    /** Gửi ảnh (URL). Nếu Telegram không tải được ảnh thì gửi caption dạng text để khách vẫn có thông tin CK. */
    public Integer sendPhoto(long chatId, String photoUrl, String caption, InlineKeyboardMarkup keyboard) {
        try {
            Message sent = client.execute(SendPhoto.builder().chatId(chatId).photo(new InputFile(photoUrl))
                    .caption(caption).parseMode(ParseMode.HTML).replyMarkup(keyboard).build());
            return sent == null ? null : sent.getMessageId();
        } catch (TelegramApiException e) {
            log.warn("sendPhoto to {} failed, fallback to text: {}", chatId, e.getMessage());
            return send(chatId, caption, keyboard);
        }
    }

    public void sendDocument(long chatId, String fileName, byte[] content, String caption) {
        try {
            client.execute(SendDocument.builder().chatId(chatId)
                    .document(new InputFile(new ByteArrayInputStream(content), fileName))
                    .caption(caption).build());
        } catch (TelegramApiException e) {
            log.warn("sendDocument to {} failed: {}", chatId, e.getMessage());
        }
    }

    /** Xóa tin nhắn (trong chat riêng, tin của bot bị xóa ở cả 2 phía). */
    public void delete(long chatId, Integer messageId) {
        if (messageId == null) {
            return;
        }
        try {
            client.execute(DeleteMessage.builder().chatId(chatId).messageId(messageId).build());
        } catch (TelegramApiException e) {
            log.debug("delete {}/{} failed: {}", chatId, messageId, e.getMessage());
        }
    }

    /** Gửi tin không kêu chuông (disable_notification). Ném lỗi để nơi gọi biết khách đã chặn bot / bị giới hạn. */
    public Integer sendSilentOrThrow(long chatId, String text) throws TelegramApiException {
        Message sent = client.execute(SendMessage.builder().chatId(chatId).text(text).disableNotification(true).build());
        return sent == null ? null : sent.getMessageId();
    }

    /** Xóa tối đa 100 tin 1 lần (cả 2 phía trong chat riêng, tin quá 48h Telegram tự bỏ qua). */
    public void deleteManyOrThrow(long chatId, List<Integer> messageIds) throws TelegramApiException {
        client.execute(DeleteMessages.builder().chatId(chatId).messageIds(messageIds).build());
    }

    public void answerCallback(String callbackId) {
        try {
            client.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (TelegramApiException e) {
            log.debug("answerCallback failed: {}", e.getMessage());
        }
    }

    public InputStream download(String fileId) throws TelegramApiException {
        return client.downloadFileAsStream(client.execute(GetFile.builder().fileId(fileId).build()));
    }
}
