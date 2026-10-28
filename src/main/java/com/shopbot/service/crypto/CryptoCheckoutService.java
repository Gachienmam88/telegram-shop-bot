package com.shopbot.service.crypto;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.CryptoInvoice;
import com.shopbot.domain.CryptoNetwork;
import com.shopbot.domain.PayMethod;
import com.shopbot.domain.ShopOrder;
import com.shopbot.domain.Topup;
import com.shopbot.service.CryptoInvoiceService;
import com.shopbot.service.CryptoWallets;
import com.shopbot.service.OrderService;
import com.shopbot.service.ShopException;
import com.shopbot.service.TopupService;

/** Tạo đơn / lệnh nạp thanh toán bằng USDT kèm invoice số tiền tròn (cùng 1 transaction). */
@Service
public class CryptoCheckoutService {

    private final OrderService orders;
    private final TopupService topups;
    private final CryptoInvoiceService invoices;
    private final CryptoWallets wallets;

    public CryptoCheckoutService(OrderService orders, TopupService topups, CryptoInvoiceService invoices,
                                 CryptoWallets wallets) {
        this.orders = orders;
        this.topups = topups;
        this.invoices = invoices;
        this.wallets = wallets;
    }

    public record OrderInvoice(ShopOrder order, CryptoInvoice invoice) {
    }

    public record TopupInvoice(Topup topup, CryptoInvoice invoice) {
    }

    @Transactional
    public OrderInvoice createOrder(long userId, long chatId, long productId, int quantity, CryptoNetwork network) {
        requireEnabled(network);
        ShopOrder o = orders.createPendingOrder(userId, chatId, productId, quantity, PayMethod.CRYPTO,
                wallets.timeoutSeconds());
        CryptoInvoice i = invoices.create(network, invoices.toUsdt(o.getAmount()), o.getAmount(), o.getPayRef(),
                o.getExpiresAt());
        return new OrderInvoice(o, i);
    }

    /** Nạp usdt USDT → ví được cộng usdt × tỷ giá (VNĐ). */
    @Transactional
    public TopupInvoice createTopup(long userId, long chatId, BigDecimal usdt, CryptoNetwork network) {
        requireEnabled(network);
        long vnd = invoices.toVnd(usdt);
        Topup t = topups.create(userId, chatId, vnd, wallets.timeoutSeconds());
        CryptoInvoice i = invoices.create(network, usdt, vnd, t.getPayRef(), t.getExpiresAt());
        return new TopupInvoice(t, i);
    }

    private void requireEnabled(CryptoNetwork network) {
        if (!wallets.enabled(network)) {
            throw new ShopException("crypto.network_disabled");
        }
    }
}
