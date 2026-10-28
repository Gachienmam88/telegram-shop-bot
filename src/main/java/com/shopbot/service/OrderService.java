package com.shopbot.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.BalanceReason;
import com.shopbot.domain.OrderStatus;
import com.shopbot.domain.PayMethod;
import com.shopbot.domain.Product;
import com.shopbot.domain.ShopOrder;
import com.shopbot.domain.StockItem;
import com.shopbot.domain.StockStatus;
import com.shopbot.repository.OrderRepository;
import com.shopbot.repository.ProductRepository;
import com.shopbot.repository.StockItemRepository;
import com.shopbot.util.CodeGenerator;

/** Vòng đời đơn hàng: giữ hàng, thanh toán ví, giao hàng, hủy. */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final StockItemRepository stock;
    private final ProductRepository products;
    private final CatalogService catalog;
    private final WalletService wallet;
    private final AffiliateService affiliate;
    private final CodeGenerator codes;
    private final ShopProperties props;
    private final Clock clock;

    public OrderService(OrderRepository orders, StockItemRepository stock, ProductRepository products,
                        CatalogService catalog, WalletService wallet, AffiliateService affiliate,
                        CodeGenerator codes, ShopProperties props, Clock clock) {
        this.orders = orders;
        this.stock = stock;
        this.products = products;
        this.catalog = catalog;
        this.wallet = wallet;
        this.affiliate = affiliate;
        this.codes = codes;
        this.props = props;
        this.clock = clock;
    }

    /** Thanh toán bằng ví: trừ tiền + lấy hàng + giao ngay, tất cả trong 1 transaction. */
    @Transactional
    public PaidOrder payWithWallet(long userId, long chatId, long productId, int quantity) {
        Product p = catalog.activeProduct(productId);
        ShopOrder o = newOrder(userId, chatId, p, quantity, PayMethod.WALLET);
        wallet.debit(userId, o.getAmount(), BalanceReason.PURCHASE, o.getCode());
        List<StockItem> items = reserve(p.getId(), quantity, o.getCode());
        // save() với id tự gán sẽ merge → phải dùng bản managed được trả về.
        return fulfill(orders.save(o), items);
    }

    /** Tạo đơn chờ chuyển khoản, giữ hàng trong ORDER_TIMEOUT_SECONDS. */
    @Transactional
    public ShopOrder createBankOrder(long userId, long chatId, long productId, int quantity) {
        return createPendingOrder(userId, chatId, productId, quantity, PayMethod.BANK, props.payment().timeoutSeconds());
    }

    /** Tạo đơn chờ thanh toán (chuyển khoản / USDT), giữ hàng trong timeoutSeconds. */
    @Transactional
    public ShopOrder createPendingOrder(long userId, long chatId, long productId, int quantity, PayMethod method,
                                        int timeoutSeconds) {
        Product p = catalog.activeProduct(productId);
        ShopOrder o = newOrder(userId, chatId, p, quantity, method);
        o.setExpiresAt(clock.instant().plus(Duration.ofSeconds(timeoutSeconds)));
        reserve(p.getId(), quantity, o.getCode());
        return orders.save(o);
    }

    /** Lưu id tin nhắn QR. Trả false nếu đơn không còn chờ thanh toán (caller nên xóa tin QR). */
    @Transactional
    public boolean attachPaymentMessage(String code, int messageId) {
        ShopOrder o = orders.findForUpdate(code).orElseThrow();
        o.setPaymentMessageId(messageId);
        return o.getStatus() == OrderStatus.PENDING;
    }

    /** Giao hàng cho đơn PENDING đã nhận đủ tiền (order phải đang bị khóa trong transaction hiện tại). */
    @Transactional
    public PaidOrder fulfillReserved(ShopOrder lockedOrder) {
        List<StockItem> items = stock.findByOrderCodeOrderByIdAsc(lockedOrder.getCode());
        return fulfill(lockedOrder, items);
    }

    /**
     * Hủy đơn đang chờ: trả hàng về kho. requesterUserId != null → chỉ chủ đơn được hủy.
     * Trả về đơn đã hủy, hoặc empty nếu đơn không còn PENDING.
     */
    @Transactional
    public Optional<ShopOrder> cancelPending(String code, Long requesterUserId) {
        return orders.findForUpdate(code)
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .filter(o -> requesterUserId == null || o.getUserId().equals(requesterUserId))
                .map(this::cancelLocked);
    }

    /** Hủy đơn đã bị khóa trong transaction hiện tại. */
    @Transactional
    public ShopOrder cancelLocked(ShopOrder o) {
        o.setStatus(OrderStatus.CANCELLED);
        stock.releaseReserved(o.getCode());
        return o;
    }

    @Transactional(readOnly = true)
    public List<String> expiredPendingCodes() {
        return orders.findCodesByStatusAndExpiresAtBefore(OrderStatus.PENDING, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ShopOrder> recentPaidOrders(long userId) {
        return orders.findTop10ByUserIdAndStatusOrderByPaidAtDesc(userId, OrderStatus.PAID);
    }

    /** Xem lại tài khoản của đơn đã mua (chỉ chủ đơn). */
    @Transactional(readOnly = true)
    public PaidOrder paidOrderOf(long userId, String code) {
        ShopOrder o = orders.findById(code)
                .filter(x -> x.getUserId().equals(userId) && x.getStatus() == OrderStatus.PAID)
                .orElseThrow(() -> new ShopException("history.not_found"));
        List<String> accounts = stock.findByOrderCodeOrderByIdAsc(code).stream().map(StockItem::getContent).toList();
        return new PaidOrder(o, accounts);
    }

    private ShopOrder newOrder(long userId, long chatId, Product p, int quantity, PayMethod method) {
        if (quantity < 1) {
            throw new ShopException("qty.invalid", catalog.availableStock(p.getId()));
        }
        ShopOrder o = new ShopOrder();
        String code;
        do {
            code = codes.orderCode();
        } while (orders.existsById(code));
        o.setCode(code);
        o.setPayRef(codes.payRef(CodeGenerator.ORDER_REF_PREFIX));
        o.setUserId(userId);
        o.setChatId(chatId);
        o.setProductId(p.getId());
        o.setProductName(p.getName());
        o.setQuantity(quantity);
        o.setUnitPrice(p.getPrice());
        o.setAmount(Math.multiplyExact(p.getPrice(), (long) quantity));
        o.setPayMethod(method);
        o.setStatus(OrderStatus.PENDING);
        o.setCreatedAt(clock.instant());
        return o;
    }

    /** Khóa đủ N tài khoản trống cho đơn, không đủ → ShopException (rollback toàn bộ). */
    private List<StockItem> reserve(long productId, int quantity, String orderCode) {
        List<StockItem> items = stock.lockAvailable(productId, quantity);
        if (items.size() < quantity) {
            throw new ShopException(items.isEmpty() ? "product.out_of_stock" : "product.not_enough_stock", items.size());
        }
        items.forEach(i -> {
            i.setStatus(StockStatus.RESERVED);
            i.setOrderCode(orderCode);
        });
        return items;
    }

    private PaidOrder fulfill(ShopOrder o, List<StockItem> items) {
        items.forEach(i -> i.setStatus(StockStatus.SOLD));
        products.incrementSold(o.getProductId(), o.getQuantity());
        o.setStatus(OrderStatus.PAID);
        o.setPaidAt(clock.instant());
        affiliate.onOrderPaid(o);
        return new PaidOrder(o, items.stream().map(StockItem::getContent).toList());
    }
}
