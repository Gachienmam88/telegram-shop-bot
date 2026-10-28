package com.shopbot.telegram.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.shopbot.domain.AccountSubmission;
import com.shopbot.domain.Withdrawal;
import com.shopbot.service.AccountSaleService;
import com.shopbot.service.MessageService;
import com.shopbot.service.ShopException;
import com.shopbot.service.UserService;
import com.shopbot.service.WithdrawalService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.AccountKeyboards;
import com.shopbot.util.Formats;

/**
 * Nút Duyệt/Từ chối (hồ sơ bán acc) và Đã chuyển/Từ chối (yêu cầu rút) mà admin bấm.
 * Chỉ admin mới được xử lý; bấm trùng (admin khác đã xử lý) sẽ báo đã xử lý rồi.
 */
@Component
public class AdminReviewHandler implements CallbackHandler {

    private final TelegramSender sender;
    private final MessageService msg;
    private final AccountSaleService sales;
    private final WithdrawalService withdrawals;
    private final UserService users;
    private final AccountKeyboards keyboards;

    public AdminReviewHandler(TelegramSender sender, MessageService msg, AccountSaleService sales,
                              WithdrawalService withdrawals, UserService users, AccountKeyboards keyboards) {
        this.sender = sender;
        this.msg = msg;
        this.sales = sales;
        this.withdrawals = withdrawals;
        this.users = users;
        this.keyboards = keyboards;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.SALE_ACCEPT, CallbackData.SALE_REJECT,
                CallbackData.WITHDRAW_PAID, CallbackData.WITHDRAW_REJECT);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        if (!ctx.admin() || parts.length < 2) {
            return;
        }
        String code = parts[1];
        try {
            switch (parts[0]) {
                case CallbackData.SALE_ACCEPT -> saleAccept(ctx, code);
                case CallbackData.SALE_REJECT -> saleReject(ctx, code);
                case CallbackData.WITHDRAW_PAID -> withdrawPaid(ctx, code);
                default -> withdrawReject(ctx, code);
            }
        } catch (IllegalArgumentException | ShopException e) {
            sender.show(ctx.chatId(), ctx.messageId(), "⚠️ " + Formats.html(message(e)), null);
        }
    }

    private void saleAccept(BotContext ctx, String code) {
        AccountSubmission s = sales.accept(code, ctx.userId());
        sender.show(ctx.chatId(), ctx.messageId(), "✅ Đã DUYỆT hồ sơ " + s.getCode() + " — cộng "
                + Formats.money(s.getAskingPrice()) + " vào ví user " + s.getUserId(), null);
        String lang = lang(s.getUserId());
        sender.send(s.getChatId(), msg.t(lang, "sell.accepted", s.getCode(), Formats.money(s.getAskingPrice())), null);
    }

    private void saleReject(BotContext ctx, String code) {
        AccountSubmission s = sales.reject(code, ctx.userId());
        sender.show(ctx.chatId(), ctx.messageId(), "❌ Đã TỪ CHỐI hồ sơ " + s.getCode(), null);
        sender.send(s.getChatId(), msg.t(lang(s.getUserId()), "sell.rejected", s.getCode()), null);
    }

    private void withdrawPaid(BotContext ctx, String code) {
        Withdrawal w = withdrawals.markPaid(code, ctx.userId());
        sender.show(ctx.chatId(), ctx.messageId(), "✅ Đã đánh dấu ĐÃ CHUYỂN cho yêu cầu rút " + w.getCode()
                + " (" + Formats.money(w.getAmount()) + ")", null);
        String lang = lang(w.getUserId());
        sender.send(w.getChatId(), msg.t(lang, "withdraw.paid", w.getCode(), Formats.money(w.getAmount())),
                keyboards.withdrawConfirm(lang, w.getCode()));
    }

    private void withdrawReject(BotContext ctx, String code) {
        Withdrawal w = withdrawals.reject(code, ctx.userId());
        sender.show(ctx.chatId(), ctx.messageId(), "❌ Đã TỪ CHỐI yêu cầu rút " + w.getCode() + " — hoàn "
                + Formats.money(w.getAmount()) + " về ví user " + w.getUserId(), null);
        sender.send(w.getChatId(), msg.t(lang(w.getUserId()), "withdraw.rejected", w.getCode(),
                Formats.money(w.getAmount())), null);
    }

    private String lang(long userId) {
        return MessageService.normalize(users.get(userId).getLang());
    }

    private String message(RuntimeException e) {
        return e instanceof ShopException se ? msg.t("vi", se.messageKey(), se.args()) : e.getMessage();
    }
}
