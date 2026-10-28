package com.shopbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import com.shopbot.domain.CryptoNetwork;
import com.shopbot.service.crypto.CryptoCheckoutService;
import com.shopbot.support.PostgresTestBase;

/** Luồng thật qua UpdateRouter: chọn SP → nhập số lượng (validate) → checkout → QR. Telegram được mock. */
class CheckoutFlowTest extends PostgresTestBase {

    private static final long USER = 100;

    @Autowired
    UpdateRouter router;

    @Autowired
    CryptoCheckoutService cryptoCheckout;

    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        Message sent = new Message();
        sent.setMessageId(555);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(sent);
        when(telegramClient.execute(any(SendPhoto.class))).thenReturn(sent);
        createUser(USER, 0);
        productId = createProduct(100_000, 3);
    }

    @Test
    void quantityValidationThenBankCheckout() throws Exception {
        router.route(callback("prod:" + productId));
        assertThat(lastText()).contains("Nhập số lượng cần mua (tối đa 3)");

        router.route(text("abc"));
        assertThat(lastText()).contains("Số lượng không hợp lệ");

        router.route(text("5"));
        assertThat(lastText()).contains("Chỉ còn 3 tài khoản");

        router.route(text("2"));
        assertThat(lastText()).contains("Ví của bạn").contains("Tổng tiền: <b>200k</b>").contains("Số dư ví không đủ");

        router.route(callback("payb"));
        ArgumentCaptor<SendPhoto> photo = ArgumentCaptor.forClass(SendPhoto.class);
        verify(telegramClient).execute(photo.capture());
        String payRef = jdbc.queryForObject("SELECT pay_ref FROM orders", String.class);
        assertThat(photo.getValue().getCaption()).contains(payRef).contains("200.000đ").contains("0366189136");
        assertThat(jdbc.queryForObject("SELECT payment_message_id FROM orders", Integer.class)).isEqualTo(555);
        assertThat(stockCount(productId, "RESERVED")).isEqualTo(2);

        router.route(callback("payb"));
        assertThat(lastText()).as("bấm lần 2 không tạo đơn mới").contains("hết hạn");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void usdtCheckoutShowsNetworkThenInvoice() throws Exception {
        router.route(callback("prod:" + productId));
        router.route(text("1"));
        router.route(callback("payc"));
        assertThat(lastText()).contains("Chọn mạng bạn sẽ chuyển USDT");

        router.route(callback("paycn:TRON"));
        SendPhoto qr = lastPhoto();
        assertThat(qr.getPhoto().getAttachName()).startsWith("https://api.qrserver.com/").endsWith("data=TJEif21DCjR1HwfdFK48Mk3XWzfoDHpTYX");
        assertThat(qr.getCaption()).contains("TJEif21DCjR1HwfdFK48Mk3XWzfoDHpTYX").contains("TRON (TRC20)")
                .contains("<code>3.71</code> USDT").contains("quét QR").contains("Phí mạng do bạn trả thêm");
        assertThat(jdbc.queryForObject("SELECT pay_method FROM orders", String.class)).isEqualTo("CRYPTO");
        assertThat(jdbc.queryForObject("SELECT payment_message_id FROM orders", Integer.class)).isEqualTo(555);
        assertThat(stockCount(productId, "RESERVED")).isEqualTo(1);
    }

    @Test
    void usdtCheckoutWithTakenAmountOffersOtherNetwork() throws Exception {
        createUser(200, 0);
        cryptoCheckout.createTopup(200, 200, new BigDecimal("3.71"), CryptoNetwork.TRON);

        router.route(callback("prod:" + productId));
        router.route(text("1"));
        router.route(callback("payc"));
        router.route(callback("paycn:TRON"));
        assertThat(lastText()).contains("Đang có khách khác thanh toán đúng <b>3.71 USDT</b> qua mạng TRON (TRC20)");
        assertThat(stockCount(productId, "RESERVED")).isZero();

        router.route(callback("paycn:BSC"));
        assertThat(lastPhoto().getCaption()).as("vẫn giữ giỏ hàng, chọn mạng khác là được")
                .contains("<code>3.71</code> USDT").contains("BNB Smart Chain (BEP20)");
        assertThat(stockCount(productId, "RESERVED")).isEqualTo(1);
    }

    @Test
    void usdtTopupAsksAmountThenNetwork() throws Exception {
        router.route(callback("topc"));
        router.route(text("0.5"));
        assertThat(lastText()).contains("Số USDT không hợp lệ");
        router.route(text("12,5"));
        router.route(callback("topcn:BSC"));
        assertThat(lastPhoto().getCaption()).contains("12.5 USDT").contains("<code>12.5</code> USDT").contains("337.500đ").contains("BNB Smart Chain (BEP20)");
    }

    @Test
    void koreanLanguageWorks() throws Exception {
        router.route(callback("lang:ko"));
        router.route(callback("wal"));
        assertThat(lastText()).contains("내 지갑");
    }

    @Test
    void languageSwitchShowsEnglishHome() throws Exception {
        router.route(callback("lang:en"));
        assertThat(jdbc.queryForObject("SELECT lang FROM users WHERE user_id = ?", String.class, USER)).isEqualTo("en");
        router.route(callback("wal"));
        assertThat(lastText()).contains("Your wallet").contains("0đ");
    }

    @Test
    void nonAdminCannotUseAdminCommands() throws Exception {
        // USER=100 không nằm trong ADMIN_IDS (test cấu hình admin-ids=1)
        router.route(text("/admin"));
        assertThat(lastText()).doesNotContain("LỆNH ADMIN").contains("AI SHOP");

        router.route(text("/addbal 100 1000000"));
        assertThat(balanceOf(USER)).as("khách tự cộng tiền không được").isZero();
    }

    @Test
    void menuCommandsOpenScreens() throws Exception {
        router.route(text("/wallet"));
        assertThat(lastText()).contains("Ví của bạn").contains("0đ");

        // /products → lưới danh mục → bấm danh mục → các gói trong danh mục
        router.route(text("/products"));
        assertThat(lastText()).contains("Danh mục sản phẩm");
        var categoryButton = lastKeyboard().getKeyboard().get(0).get(0);
        assertThat(categoryButton.getText()).isEqualTo("🎬 Test (3)");

        router.route(callback(categoryButton.getCallbackData()));
        assertThat(lastText()).contains("🎬 Test");
        assertThat(lastKeyboard().getKeyboard().get(0).get(0).getText()).isEqualTo("✂️ CapCut Pro | 100k | 📦 3");
    }

    /** Bàn phím inline của tin gần nhất bot gửi. */
    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup lastKeyboard() throws Exception {
        ArgumentCaptor<BotApiMethod> captor = ArgumentCaptor.forClass(BotApiMethod.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        List<Object> markups = captor.getAllValues().stream()
                .map(m -> m instanceof SendMessage s ? s.getReplyMarkup() : m instanceof EditMessageText e ? e.getReplyMarkup() : null)
                .filter(java.util.Objects::nonNull)
                .map(Object.class::cast)
                .toList();
        return (org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup) markups.get(markups.size() - 1);
    }

    /** Ảnh gần nhất bot gửi (QR thanh toán). */
    private SendPhoto lastPhoto() throws Exception {
        ArgumentCaptor<SendPhoto> captor = ArgumentCaptor.forClass(SendPhoto.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    /** Text của tin gần nhất bot gửi mới (SendMessage) hoặc sửa (EditMessageText). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String lastText() throws Exception {
        ArgumentCaptor<BotApiMethod> captor = ArgumentCaptor.forClass(BotApiMethod.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        List<String> texts = captor.getAllValues().stream()
                .map(m -> m instanceof SendMessage s ? s.getText() : m instanceof EditMessageText e ? e.getText() : null)
                .filter(t -> t != null)
                .toList();
        return texts.get(texts.size() - 1);
    }

    private static User user() {
        return new User(USER, "Test", false);
    }

    private static Message incoming(String text) {
        Message m = new Message();
        m.setMessageId(1);
        m.setText(text);
        m.setFrom(user());
        m.setChat(new Chat(USER, "private"));
        return m;
    }

    private static Update text(String text) {
        Update u = new Update();
        u.setMessage(incoming(text));
        return u;
    }

    private static Update callback(String data) {
        CallbackQuery cq = new CallbackQuery();
        cq.setId("cb");
        cq.setFrom(user());
        cq.setData(data);
        cq.setMessage(incoming("menu"));
        Update u = new Update();
        u.setCallbackQuery(cq);
        return u;
    }
}
