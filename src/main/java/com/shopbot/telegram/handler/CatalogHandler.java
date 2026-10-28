package com.shopbot.telegram.handler;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.shopbot.domain.Category;
import com.shopbot.domain.ConversationStep;
import com.shopbot.domain.Product;
import com.shopbot.service.CatalogService;
import com.shopbot.service.CatalogService.CategoryView;
import com.shopbot.service.CatalogService.ProductView;
import com.shopbot.service.ConversationService;
import com.shopbot.service.MessageService;
import com.shopbot.telegram.BotContext;
import com.shopbot.telegram.CallbackData;
import com.shopbot.telegram.CallbackHandler;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.keyboard.ShopKeyboards;
import com.shopbot.util.Formats;

/** Lưới danh mục → các gói trong danh mục (kèm tồn kho) → chi tiết SP (+ hỏi số lượng nếu còn hàng). */
@Component
public class CatalogHandler implements CallbackHandler {

    private final TelegramSender sender;
    private final MessageService msg;
    private final ShopKeyboards keyboards;
    private final CatalogService catalog;
    private final ConversationService conversations;

    public CatalogHandler(TelegramSender sender, MessageService msg, ShopKeyboards keyboards, CatalogService catalog,
                          ConversationService conversations) {
        this.sender = sender;
        this.msg = msg;
        this.keyboards = keyboards;
        this.catalog = catalog;
        this.conversations = conversations;
    }

    @Override
    public List<String> actions() {
        return List.of(CallbackData.CATEGORIES, CallbackData.CATEGORY, CallbackData.PRODUCT);
    }

    @Override
    public void handle(BotContext ctx, String[] parts) {
        if (CallbackData.PRODUCT.equals(parts[0])) {
            showProduct(ctx, Long.parseLong(parts[1]));
        } else if (CallbackData.CATEGORY.equals(parts[0])) {
            showCategory(ctx, Long.parseLong(parts[1]));
        } else {
            showCategoryList(ctx);
        }
    }

    /** "Xem sản phẩm": lưới danh mục có SP đang bán. */
    public void showCategoryList(BotContext ctx) {
        conversations.clear(ctx.userId());
        List<CategoryView> categories = catalog.activeCategories();
        if (categories.isEmpty()) {
            sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "catalog.empty"), keyboards.backHome(ctx.lang()));
            return;
        }
        sender.show(ctx.chatId(), ctx.messageId(), msg.t(ctx.lang(), "catalog.title"),
                keyboards.categoryList(ctx.lang(), categories));
    }

    /** Các gói trong 1 danh mục, mỗi dòng "Tên | Giá | 📦 tồn"; danh mục rỗng/không tồn tại (nút cũ) → về lưới danh mục. */
    public void showCategory(BotContext ctx, long categoryId) {
        conversations.clear(ctx.userId());
        Optional<Category> category = catalog.category(categoryId);
        List<ProductView> products = category.isPresent() ? catalog.activeProducts(categoryId) : List.of();
        if (products.isEmpty()) {
            showCategoryList(ctx);
            return;
        }
        sender.show(ctx.chatId(), ctx.messageId(),
                msg.t(ctx.lang(), "catalog.products_title", Formats.html(category.get().label())),
                keyboards.productList(ctx.lang(), categoryId, products));
    }

    /** Chi tiết SP; còn hàng thì gửi thêm tin "Nhập số lượng" và chờ khách gõ. */
    public void showProduct(BotContext ctx, long productId) {
        Product p = catalog.activeProduct(productId);
        int stock = catalog.availableStock(productId);
        sender.show(ctx.chatId(), ctx.messageId(), detailText(ctx.lang(), p, stock),
                keyboards.productDetail(ctx.lang(), p.getCategoryId()));
        if (stock > 0) {
            conversations.set(ctx.userId(), ConversationStep.AWAIT_QUANTITY, String.valueOf(productId));
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "qty.prompt", stock), null);
        } else {
            conversations.clear(ctx.userId());
            sender.send(ctx.chatId(), msg.t(ctx.lang(), "product.out_of_stock"), null);
        }
    }

    public String detailText(String lang, Product p, long stock) {
        String text = msg.t(lang, "product.detail", Formats.html(p.label()), Formats.price(p.getPrice()), stock, p.getSold());
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            // Mô tả là HTML do admin nhập (đã kiểm tra hợp lệ lúc /addproduct, /setdesc) nên không escape.
            text += msg.t(lang, "product.description", p.getDescription());
        }
        return text;
    }
}
