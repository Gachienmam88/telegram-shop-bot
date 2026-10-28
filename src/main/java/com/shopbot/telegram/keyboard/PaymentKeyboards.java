package com.shopbot.telegram.keyboard;

import static com.shopbot.telegram.keyboard.Buttons.cb;
import static com.shopbot.telegram.keyboard.Buttons.row;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.CryptoNetwork;
import com.shopbot.domain.ShopOrder;
import com.shopbot.service.CryptoWallets;
import com.shopbot.service.MessageService;
import com.shopbot.telegram.CallbackData;
import com.shopbot.util.Formats;

/** Bàn phím: checkout, tin QR đơn hàng, ví, nạp tiền, lịch sử mua. */
@Component
public class PaymentKeyboards {

    private final MessageService msg;
    private final ShopProperties props;
    private final CryptoWallets cryptoWallets;

    public PaymentKeyboards(MessageService msg, ShopProperties props, CryptoWallets cryptoWallets) {
        this.msg = msg;
        this.props = props;
        this.cryptoWallets = cryptoWallets;
    }

    public InlineKeyboardMarkup checkout(String lang, boolean canPayByWallet) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (canPayByWallet) {
            rows.add(row(cb(msg.t(lang, "btn.pay_wallet"), CallbackData.PAY_WALLET)));
        }
        rows.add(row(cb(msg.t(lang, "btn.pay_bank"), CallbackData.PAY_BANK)));
        if (cryptoWallets.anyEnabled()) {
            rows.add(row(cb(msg.t(lang, "btn.pay_crypto"), CallbackData.PAY_CRYPTO)));
        }
        rows.add(row(cb(msg.t(lang, "btn.cancel"), CallbackData.CHECKOUT_CANCEL)));
        return Buttons.markup(rows);
    }

    /** Mỗi mạng đang bật 1 nút (action:BSC ...), cuối là Hủy → về trang chủ. */
    public InlineKeyboardMarkup cryptoNetworks(String lang, String action) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (CryptoNetwork n : cryptoWallets.enabledNetworks()) {
            rows.add(row(cb("💎 USDT · " + n.displayName(), CallbackData.of(action, n.name()))));
        }
        rows.add(row(cb(msg.t(lang, "btn.cancel"), CallbackData.CHECKOUT_CANCEL)));
        return Buttons.markup(rows);
    }

    /** Nút dưới tin QR đơn hàng: Liên hệ admin, Hủy đơn, Lịch sử mua, Ví, Sản phẩm, Trang chủ. */
    public InlineKeyboardMarkup bankOrder(String lang, String orderCode) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        String support = supportUrl();
        if (support != null) {
            rows.add(row(Buttons.url(msg.t(lang, "btn.contact_admin"), support),
                    cb(msg.t(lang, "btn.cancel_order"), CallbackData.of(CallbackData.ORDER_CANCEL, orderCode))));
        } else {
            rows.add(row(cb(msg.t(lang, "btn.cancel_order"), CallbackData.of(CallbackData.ORDER_CANCEL, orderCode))));
        }
        rows.add(row(cb(msg.t(lang, "btn.history"), CallbackData.HISTORY), cb(msg.t(lang, "btn.wallet"), CallbackData.WALLET)));
        rows.add(row(cb(msg.t(lang, "btn.products"), CallbackData.CATEGORIES), cb(msg.t(lang, "btn.home"), CallbackData.HOME)));
        return Buttons.markup(rows);
    }

    public InlineKeyboardMarkup afterPurchase(String lang) {
        return Buttons.markup(
                row(cb(msg.t(lang, "btn.history"), CallbackData.HISTORY), cb(msg.t(lang, "btn.products"), CallbackData.CATEGORIES)),
                row(cb(msg.t(lang, "btn.home"), CallbackData.HOME)));
    }

    public InlineKeyboardMarkup wallet(String lang) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(row(cb(msg.t(lang, "btn.topup"), CallbackData.TOPUP)));
        if (cryptoWallets.anyEnabled()) {
            rows.add(row(cb(msg.t(lang, "btn.topup_crypto"), CallbackData.TOPUP_CRYPTO)));
        }
        rows.add(row(cb(msg.t(lang, "btn.withdraw"), CallbackData.WITHDRAW),
                cb(msg.t(lang, "btn.balance_log"), CallbackData.BALANCE_LOG)));
        rows.add(row(cb(msg.t(lang, "btn.home"), CallbackData.HOME)));
        return Buttons.markup(rows);
    }

    public InlineKeyboardMarkup topupQr(String lang, String paymentPageUrl) {
        return Buttons.markup(
                row(Buttons.url(msg.t(lang, "btn.open_payment"), paymentPageUrl)),
                row(cb(msg.t(lang, "btn.view_wallet"), CallbackData.WALLET), cb(msg.t(lang, "btn.home"), CallbackData.HOME)));
    }

    public InlineKeyboardMarkup backWallet(String lang) {
        return Buttons.markup(row(cb(msg.t(lang, "btn.view_wallet"), CallbackData.WALLET),
                cb(msg.t(lang, "btn.home"), CallbackData.HOME)));
    }

    /** Mỗi đơn 1 nút để xem lại tài khoản đã mua. */
    public InlineKeyboardMarkup history(String lang, List<ShopOrder> orders) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (ShopOrder o : orders) {
            String label = msg.t(lang, "history.row", Formats.time(o.getPaidAt(), props.zone()),
                    Formats.truncate(o.getProductName(), 28), o.getQuantity());
            rows.add(row(cb(label, CallbackData.of(CallbackData.HISTORY_ORDER, o.getCode()))));
        }
        rows.add(row(cb(msg.t(lang, "btn.home"), CallbackData.HOME)));
        return Buttons.markup(rows);
    }

    /** Link chat với admin hỗ trợ, null nếu chưa cấu hình SUPPORT_USERNAME. */
    public String supportUrl() {
        String u = props.bot().supportUsername();
        return u == null || u.isBlank() ? null : "https://t.me/" + u.replace("@", "");
    }
}
