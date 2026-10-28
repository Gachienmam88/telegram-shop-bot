package com.shopbot.telegram.admin;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.shopbot.domain.OrderStatus;
import com.shopbot.domain.TopupStatus;
import com.shopbot.repository.OrderRepository;
import com.shopbot.repository.TopupRepository;
import com.shopbot.service.AffiliateService;
import com.shopbot.service.IncomingPaymentService;
import com.shopbot.service.MessageService;
import com.shopbot.service.PaymentOutcome;
import com.shopbot.service.StatsService;
import com.shopbot.service.UserService;
import com.shopbot.service.WalletService;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.util.CodeGenerator;
import com.shopbot.util.Formats;

/** Lệnh admin: xác nhận thanh toán tay, điều chỉnh ví, hoa hồng affiliate, thống kê. */
@Component
public class AdminPaymentCommands implements AdminCommandSet {

    private final IncomingPaymentService payments;
    private final OrderRepository orders;
    private final TopupRepository topups;
    private final WalletService wallet;
    private final UserService users;
    private final AffiliateService affiliate;
    private final StatsService stats;
    private final MessageService msg;
    private final TelegramSender sender;

    public AdminPaymentCommands(IncomingPaymentService payments, OrderRepository orders, TopupRepository topups,
                                WalletService wallet, UserService users, AffiliateService affiliate,
                                StatsService stats, MessageService msg, TelegramSender sender) {
        this.payments = payments;
        this.orders = orders;
        this.topups = topups;
        this.wallet = wallet;
        this.users = users;
        this.affiliate = affiliate;
        this.stats = stats;
        this.msg = msg;
        this.sender = sender;
    }

    @Override
    public Map<String, AdminCommand> commands() {
        return Map.of("/confirm", this::confirm, "/addbal", this::addBalance, "/setaff", this::setAffiliate,
                "/stats", this::stats);
    }

    /** Xác nhận tay khi webhook không tới: coi như khách đã chuyển đúng số tiền của đơn/nạp. */
    private void confirm(AdminRequest r) {
        String ref = r.args().trim().toUpperCase(Locale.ROOT);
        long amount;
        if (ref.startsWith(CodeGenerator.TOPUP_REF_PREFIX)) {
            var t = topups.findByPayRef(ref).orElseThrow(() -> new IllegalArgumentException("Không có mã nạp " + ref));
            if (t.getStatus() == TopupStatus.PAID) {
                throw new IllegalArgumentException("Mã nạp này đã được cộng tiền rồi");
            }
            amount = t.getAmount();
        } else if (ref.startsWith(CodeGenerator.ORDER_REF_PREFIX)) {
            var o = orders.findByPayRef(ref).orElseThrow(() -> new IllegalArgumentException("Không có mã đơn " + ref));
            if (o.getStatus() == OrderStatus.PAID) {
                throw new IllegalArgumentException("Đơn này đã thanh toán rồi");
            }
            amount = o.getAmount();
        } else {
            throw new IllegalArgumentException("Cú pháp: /confirm DHxxxxxxxx hoặc /confirm NAPxxxxxxxx");
        }
        PaymentOutcome out = payments.receive(null, amount, ref);
        reply(r, "✅ Đã xác nhận " + ref + " (" + Formats.money(amount) + ") → " + out.getClass().getSimpleName());
    }

    /** /addbal user_id số_tiền (âm = trừ). */
    private void addBalance(AdminRequest r) {
        String[] w = r.words();
        if (w.length < 2) {
            throw new IllegalArgumentException("Cú pháp: /addbal user_id số_tiền (âm để trừ)");
        }
        long userId = AdminRequest.parseLong(w[0], "user_id");
        long delta = AdminRequest.parseLong(w[1], "Số tiền");
        if (delta == 0) {
            throw new IllegalArgumentException("Số tiền phải khác 0");
        }
        long balance = wallet.adjust(userId, delta, "admin:" + r.ctx().userId());
        reply(r, "✅ User " + userId + ": " + Formats.signedMoney(delta) + " → số dư " + Formats.money(balance));
        String lang = MessageService.normalize(users.get(userId).getLang());
        sender.send(userId, msg.t(lang, "wallet.admin_adjust", Formats.signedMoney(delta), Formats.money(balance)), null);
    }

    private void setAffiliate(AdminRequest r) {
        int pct = (int) AdminRequest.parseLong(r.args(), "Phần trăm");
        if (pct < 0 || pct > 100) {
            throw new IllegalArgumentException("Phần trăm phải từ 0 đến 100");
        }
        affiliate.setPercent(pct);
        reply(r, pct == 0 ? "✅ Đã TẮT hoa hồng affiliate" : "✅ Hoa hồng affiliate: " + pct + "% mỗi đơn");
    }

    private void stats(AdminRequest r) {
        StatsService.Stats s = stats.stats();
        reply(r, "📊 <b>Thống kê</b>\n👥 Người dùng: " + s.users()
                + "\n\n<b>Hôm nay</b>\n🧾 Đơn: " + s.ordersToday() + "\n💰 Doanh thu: " + Formats.money(s.revenueToday())
                + "\n\n<b>Tổng</b>\n🧾 Đơn: " + s.ordersTotal() + "\n💰 Doanh thu: " + Formats.money(s.revenueTotal())
                + "\n\n🤝 Hoa hồng affiliate: " + (affiliate.percent() == 0 ? "tắt" : affiliate.percent() + "%"));
    }

    private void reply(AdminRequest r, String html) {
        sender.send(r.ctx().chatId(), html, null);
    }
}
