package com.shopbot.telegram.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.shopbot.domain.ConversationStep;
import com.shopbot.domain.UserAccount;
import com.shopbot.domain.Withdrawal;
import com.shopbot.service.ConversationService;
import com.shopbot.service.MessageService;
import com.shopbot.service.UserService;
import com.shopbot.service.WalletService;
import com.shopbot.service.WithdrawalService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.PaymentOutcomeNotifier;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.AccountKeyboards;
import com.shopbot.telegram.keyboard.PaymentKeyboards;
import com.shopbot.telegram.keyboard.ShopKeyboards;
import com.shopbot.util.Formats;
import com.shopbot.util.InputValidators;

/** Rút tiền: nhập thông tin ngân hàng (lưu 1 lần) → nhập số tiền → admin duyệt → người dùng xác nhận đã nhận. */
@Component
public class WithdrawHandler implements CallbackHandler {

    /** Đánh dấu đang trong luồng rút tiền để sau khi lưu ngân hàng thì hỏi tiếp số tiền. */
    private static final String FROM_WITHDRAW = "withdraw";

    private final TelegramSender sender;
    private final MessageService msg;
    private final ConversationService conversations;
    private final WalletService wallet;
    private final WithdrawalService withdrawals;
    private final UserService users;
    private final AccountKeyboards keyboards;
    private final PaymentKeyboards paymentKeyboards;
    private final ShopKeyboards shopKeyboards;
    private final PaymentOutcomeNotifier notifier;

    public WithdrawHandler(TelegramSender sender, MessageService msg, ConversationService conversations,
                           WalletService wallet, WithdrawalService withdrawals, UserService users,
                           AccountKeyboards keyboards, PaymentKeyboards paymentKeyboards, ShopKeyboards shopKeyboards,
                           PaymentOutcomeNotifier notifier) {
        this.sender = sender;
        this.msg = msg;
        this.conversations = conversations;
        this.wallet = wallet;
        this.withdrawals = withdrawals;
        this.users = users;
        this.keyboards = keyboards;
        this.paymentKeyboards = paymentKeyboards;
        this.shopKeyboards = shopKeyboards;
        this.notifier = notifier;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.WITHDRAW, CallbackData.BANK_EDIT, CallbackData.WITHDRAW_CONFIRM);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        switch (parts[0]) {
            case CallbackData.BANK_EDIT -> askBankInfo(ctx, null);
            case CallbackData.WITHDRAW_CONFIRM -> confirmReceived(ctx, parts.length > 1 ? parts[1] : "");
            default -> startWithdraw(ctx);
        }
    }

    private void startWithdraw(BotContext ctx) {
        UserAccount u = users.get(ctx.userId());
        if (!u.hasBankInfo()) {
            askBankInfo(ctx, FROM_WITHDRAW);
            return;
        }
        askAmount(ctx, u);
    }

    private void askBankInfo(BotContext ctx, String flow) {
        conversations.set(ctx.userId(), ConversationStep.AWAIT_BANK_INFO, flow);
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "withdraw.ask_bank"), shopKeyboards.backHome(ctx.lang()));
    }

    private void askAmount(BotContext ctx, UserAccount u) {
        conversations.set(ctx.userId(), ConversationStep.AWAIT_WITHDRAW_AMOUNT, null);
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "withdraw.ask_amount", Formats.money(wallet.balance(ctx.userId())),
                Formats.money(WithdrawalService.MIN_AMOUNT), Formats.html(u.getBankName()),
                Formats.html(u.getBankAccountNumber()), Formats.html(u.getBankAccountHolder())),
                shopKeyboards.backHome(ctx.lang()));
    }

    /** Người dùng gửi 3 dòng: ngân hàng / số TK / chủ TK. */
    public void onBankInfo(BotContext ctx, String flow, String text) {
        String[] info = parseBankInfo(text);
        if (info == null) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "withdraw.invalid_bank"), shopKeyboards.backHome(ctx.lang()));
            return;
        }
        users.setBankInfo(ctx.userId(), info[0], info[1], info[2]);
        conversations.clear(ctx.userId());
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "withdraw.bank_saved", Formats.html(info[0]), Formats.html(info[1]),
                Formats.html(info[2])), null);
        if (FROM_WITHDRAW.equals(flow)) {
            askAmount(ctx, users.get(ctx.userId()));
        } else {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "wallet.text", ctx.userId(),
                    Formats.money(wallet.balance(ctx.userId()))), paymentKeyboards.wallet(ctx.lang()));
        }
    }

    /** Người dùng gõ số tiền muốn rút. */
    public void onAmount(BotContext ctx, String text) {
        long balance = wallet.balance(ctx.userId());
        if (balance < WithdrawalService.MIN_AMOUNT) {
            conversations.clear(ctx.userId());
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "withdraw.balance_low",
                    Formats.money(WithdrawalService.MIN_AMOUNT), Formats.money(balance)),
                    paymentKeyboards.backWallet(ctx.lang()));
            return;
        }
        InputValidators.Result r = InputValidators.amount(text, WithdrawalService.MIN_AMOUNT, balance);
        if (!r.ok()) {
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "withdraw.invalid_amount",
                    Formats.money(WithdrawalService.MIN_AMOUNT), Formats.money(balance)),
                    shopKeyboards.backHome(ctx.lang()));
            return;
        }
        conversations.clear(ctx.userId());
        Withdrawal w = withdrawals.request(ctx.userId(), ctx.chatId(), r.value());
        sender.send(ctx.chatId(), msg.t(ctx.lang(), "withdraw.requested", w.getCode(), Formats.money(w.getAmount()),
                Formats.money(wallet.balance(ctx.userId()))), paymentKeyboards.backWallet(ctx.lang()));
        notifier.notifyAdmins(msg.t("vi", "withdraw.admin_notify", w.getCode(), ctx.userId(), Formats.money(w.getAmount()),
                Formats.html(w.getBankName()), Formats.html(w.getBankAccountNumber()), Formats.html(w.getBankAccountHolder())),
                keyboards.withdrawReview(w.getCode()));
    }

    private void confirmReceived(BotContext ctx, String code) {
        Withdrawal w = withdrawals.confirm(code, ctx.userId());
        sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "withdraw.confirmed", w.getCode()),
                paymentKeyboards.backWallet(ctx.lang()));
        notifier.notifyAdmins(msg.t("vi", "withdraw.admin_confirmed", w.getCode(), ctx.userId()));
    }

    /** 3 dòng không rỗng; dòng 2 (số TK) phải có ít nhất 6 chữ số. Trả null nếu sai định dạng. */
    private String[] parseBankInfo(String text) {
        String[] lines = text.strip().split("\\r?\\n");
        if (lines.length < 3) {
            return null;
        }
        String name = lines[0].trim();
        String number = lines[1].trim();
        String holder = lines[2].trim();
        if (name.isEmpty() || holder.isEmpty() || number.replaceAll("\\D", "").length() < 6) {
            return null;
        }
        return new String[] {name, number, holder};
    }
}
