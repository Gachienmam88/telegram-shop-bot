package com.shopbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import com.shopbot.support.PostgresTestBase;

/** /wipechats: xóa chat (cả 2 phía) với mọi khách theo lô 100 tin, bỏ qua admin và khách đã chặn bot. */
class ChatWiperTest extends PostgresTestBase {

    private static final long ADMIN = 1;
    private static final long CUSTOMER = 100;
    private static final long BLOCKED = 200;

    @Autowired
    ChatWiper wiper;

    @Autowired
    UpdateRouter router;

    @BeforeEach
    void setUp() throws Exception {
        createUser(ADMIN, 0);
        createUser(CUSTOMER, 0);
        createUser(BLOCKED, 0);
        when(telegramClient.execute(any(SendMessage.class))).thenAnswer(inv -> {
            SendMessage m = inv.getArgument(0);
            if (m.getChatId().equals(String.valueOf(BLOCKED))) {
                throw new TelegramApiRequestException("Forbidden: bot was blocked by the user");
            }
            Message sent = new Message();
            sent.setMessageId(250);
            return sent;
        });
    }

    @Test
    void deletesWholeCustomerChatInBatchesAndSkipsAdmin() throws Exception {
        ChatWiper.Result r = wiper.wipeAll(1000, p -> { });

        assertThat(r).isEqualTo(new ChatWiper.Result(2, 2, 1, 1, 1, false, 0, null));
        assertThat(ChatWiper.progressText(r, System.nanoTime())).contains("Đã xóa xong").contains("2/2 khách (100%)");
        List<DeleteMessages> deletes = deletes();
        assertThat(deletes).allMatch(d -> d.getChatId().equals(String.valueOf(CUSTOMER)));
        assertThat(deletes).extracting(d -> d.getMessageIds().size()).containsExactly(100, 100, 50);
        assertThat(deletes.get(0).getMessageIds()).contains(250, 151);
        assertThat(deletes.get(2).getMessageIds()).contains(50, 1);
        SendMessage probe = sentMessages().get(0);
        assertThat(probe.getDisableNotification()).as("không kêu chuông cho khách").isTrue();
    }

    @Test
    void depthLimitsHowFarBackItScans() throws Exception {
        wiper.wipeAll(120, p -> { });

        assertThat(deletes()).extracting(d -> d.getMessageIds().size()).containsExactly(100, 20);
        assertThat(deletes().get(1).getMessageIds()).contains(131, 150);
    }

    @Test
    void progressTextShowsPercentAndStopHint() {
        String text = ChatWiper.progressText(new ChatWiper.Result(160, 40, 38, 2, 1, false, 3, "Bad Request: message can't be deleted"), System.nanoTime());

        assertThat(text).contains("Đang xóa chat").contains("40/160 khách (25%)").contains("/wipechats STOP")
                .contains("từ chối xóa: 3").contains("message can't be deleted");
    }

    @Test
    void stopWithoutRunningJobSaysNothingToStop() throws Exception {
        router.route(adminText("/wipechats STOP"));

        assertThat(sentMessages()).extracting(SendMessage::getText).last().asString()
                .contains("Không có lượt xóa chat nào đang chạy");
    }

    @Test
    void commandWithoutConfirmWordOnlyWarns() throws Exception {
        router.route(adminText("/wipechats"));

        assertThat(sentMessages()).extracting(SendMessage::getText).last().asString()
                .contains("Xóa TOÀN BỘ tin nhắn").contains("/wipechats XACNHAN");
        assertThat(deletes()).isEmpty();
    }

    private List<DeleteMessages> deletes() throws Exception {
        return calls().stream().filter(DeleteMessages.class::isInstance).map(DeleteMessages.class::cast).toList();
    }

    private List<SendMessage> sentMessages() throws Exception {
        return calls().stream().filter(SendMessage.class::isInstance).map(SendMessage.class::cast).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<BotApiMethod> calls() throws Exception {
        ArgumentCaptor<BotApiMethod> captor = ArgumentCaptor.forClass(BotApiMethod.class);
        try {
            verify(telegramClient, atLeastOnce()).execute(captor.capture());
        } catch (AssertionError none) {
            return List.of();
        }
        return captor.getAllValues();
    }

    private static Update adminText(String text) {
        Message m = new Message();
        m.setMessageId(1);
        m.setText(text);
        m.setFrom(new User(ADMIN, "Admin", false));
        m.setChat(new Chat(ADMIN, "private"));
        Update u = new Update();
        u.setMessage(m);
        return u;
    }
}
