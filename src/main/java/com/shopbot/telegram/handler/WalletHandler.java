package com.shopbot.telegram.handler;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.BalanceLog;
import com.shopbot.domain.ConversationStep;
import com.shopbot.domain.Topup;
import com.shopbot.service.ConversationService;
import com.shopbot.service.MessageService;
import com.shopbot.service.TopupService;
import com.shopbot.service.VietQrService;
import com.shopbot.service.WalletService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.PaymentKeyboards;
import com.shopbot.telegram.keyboard.ShopKeyboards;
import com.shopbot.util.Formats;
import com.shopbot.util.InputValidators;

/** Ví: xem số dư, nạp tiền QR, lịch sử biến động số dư. */
@Component
public class WalletHandler implements CallbackHandler {

    private final TelegramSender sender;
    private final MessageService msg;
    private final PaymentKeyboards keyboards;
    private final ShopKeyboards shopKeyboards;
    private final WalletService wallet;
    private final TopupService topups;
    private final ConversationService conversations;
    private final VietQrService vietQr;
    private final ShopProperties props;

    public WalletHandler(TelegramSender sender, MessageService msg, PaymentKeyboards keyboards,
                         ShopKeyboards shopKeyboards, WalletService wallet, TopupService topups,
                         ConversationService conversations, VietQrService vietQr, ShopProperties props) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
        this.shopKeyboards = shopKeyboards;
        this.wallet = wallet;
        this.topups = topups;
        this.conversations = conversations;
        this.vietQr = vietQr;
        this.props = props;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.WALLET, CallbackData.TOPUP, CallbackData.BALANCE_LOG);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        switch (parts[0]) {
            case CallbackData.TOPUP -> askTopupAmount(ctx);
            case CallbackData.BALANCE_LOG -> showBalanceLog(ctx);
            default -> showWallet(ctx);
        }
    }

    private void showWallet(BotContext ctx) {
        conversations.clear(ctx.userId());
        sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "wallet.text", ctx.userId(),
                Formats.money(wallet.balance(ctx.userId()))), keyboards.wallet(ctx.lang()));
    }

    private void askTopupAmount(BotContext ctx) {
        conversations.set(ctx.userId(), ConversationStep.AWAIT_TOPUP_AMOUNT, null);
        ShopProperties.Payment p = props.payment();
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "topup.prompt", Formats.money(p.topupMin()),
                Formats.money(p.topupMax())), shopKeyboards.backHome(ctx.lang()));
    }

    /** Khách gõ số tiền khi đang ở bước AWAIT_TOPUP_AMOUNT. */
    public void onTopupAmount(BotContext ctx, String text) {
        ShopProperties.Payment p = props.payment();
        InputValidators.Result r = InputValidators.amount(text, p.topupMin(), p.topupMax());
        if (!r.ok()) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "topup.invalid", Formats.money(p.topupMin()),
                    Formats.money(p.topupMax())), shopKeyboards.backHome(ctx.lang()));
            return;
        }
        conversations.clear(ctx.userId());
        Topup t = topups.create(ctx.userId(), ctx.chatId(), r.value());
        ShopProperties.Bank bank = props.bank();
        String caption = msg.t(ctx.lang(), "topup.caption", Formats.money(t.getAmount()), t.getCode(),
                Formats.time(t.getExpiresAt(), props.zone()), Formats.html(bank.displayName()),
                Formats.html(bank.accountName()), bank.accountNumber(), Formats.money(t.getAmount()), t.getPayRef());
        Integer messageId = sender.sendPhoto(ctx.chatId(), vietQr.qrImageUrl(t.getAmount(), t.getPayRef()), caption,
                keyboards.topupQr(ctx.lang(), vietQr.paymentPageUrl(t.getAmount(), t.getPayRef())));
        if (messageId != null && !topups.attachPaymentMessage(t.getCode(), messageId)) {
            sender.delete(ctx.chatId(), messageId);
        }
    }

    private void showBalanceLog(BotContext ctx) {
        List<BalanceLog> logs = wallet.recentLogs(ctx.userId());
        if (logs.isEmpty()) {
            sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "balance_log.empty"),
                    keyboards.backWallet(ctx.lang()));
            return;
        }
        String rows = logs.stream()
                .map(l -> msg.t(ctx.lang(), "balance_log.row", Formats.signedMoney(l.getDelta()),
                        msg.t(ctx.lang(), "reason." + l.getReason().name()), l.getRef() == null ? "" : l.getRef(),
                        Formats.time(l.getCreatedAt(), props.zone()), Formats.money(l.getBalanceAfter())))
                .collect(Collectors.joining("\n\n"));
        sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "balance_log.title") + "\n\n" + rows,
                keyboards.backWallet(ctx.lang()));
    }
}
