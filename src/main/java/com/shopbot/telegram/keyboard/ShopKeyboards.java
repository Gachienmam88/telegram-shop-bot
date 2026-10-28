package com.shopbot.telegram.keyboard;

import static com.shopbot.telegram.keyboard.Buttons.cb;
import static com.shopbot.telegram.keyboard.Buttons.row;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import com.shopbot.domain.Product;
import com.shopbot.service.CatalogService.CategoryView;
import com.shopbot.service.CatalogService.ProductView;
import com.shopbot.service.MessageService;
import com.shopbot.telegram.CallbackData;
import com.shopbot.util.Formats;

/** Bàn phím: chọn ngôn ngữ, trang chủ, danh mục, sản phẩm. */
@Component
public class ShopKeyboards {

    private final MessageService msg;

    public ShopKeyboards(MessageService msg) {
        this.msg = msg;
    }

    public InlineKeyboardMarkup language() {
        return Buttons.markup(
                row(cb("🇻🇳 Tiếng Việt", CallbackData.of(CallbackData.LANG, "vi")),
                        cb("🇬🇧 English", CallbackData.of(CallbackData.LANG, "en")),
                        cb("🇹🇭 ภาษาไทย", CallbackData.of(CallbackData.LANG, "th"))),
                row(cb("🇨🇳 简体中文", CallbackData.of(CallbackData.LANG, "zh")),
                        cb("🇰🇷 한국어", CallbackData.of(CallbackData.LANG, "ko"))));
    }

    public InlineKeyboardMarkup home(String lang) {
        return Buttons.markup(
                row(btn(lang, "btn.products", CallbackData.CATEGORIES), btn(lang, "btn.guide", CallbackData.GUIDE)),
                row(btn(lang, "btn.history", CallbackData.HISTORY), btn(lang, "btn.wallet", CallbackData.WALLET)),
                row(btn(lang, "btn.warranty", CallbackData.WARRANTY), btn(lang, "btn.support", CallbackData.SUPPORT)),
                row(btn(lang, "btn.affiliate", CallbackData.AFFILIATE), btn(lang, "btn.sell_account", CallbackData.SELL_MENU)),
                row(btn(lang, "btn.language", CallbackData.LANG_MENU), btn(lang, "btn.home", CallbackData.HOME)));
    }

    public InlineKeyboardMarkup backHome(String lang) {
        return Buttons.markup(row(btn(lang, "btn.home", CallbackData.HOME)));
    }

    /** "Danh mục sản phẩm": lưới 2 cột "🤖 ChatGPT (tổng tồn kho)", cuối là Làm mới / Trang chủ / Hỗ trợ. */
    public InlineKeyboardMarkup categoryList(String lang, List<CategoryView> views) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < views.size(); i += 2) {
            rows.add(new InlineKeyboardRow(views.subList(i, Math.min(i + 2, views.size())).stream()
                    .map(v -> cb(msg.t(lang, "category.row", v.category().label(), v.stock()),
                            CallbackData.of(CallbackData.CATEGORY, v.category().getId())))
                    .toList()));
        }
        rows.add(row(btn(lang, "btn.refresh", CallbackData.CATEGORIES)));
        rows.add(row(btn(lang, "btn.home", CallbackData.HOME), btn(lang, "btn.support_short", CallbackData.SUPPORT)));
        return Buttons.markup(rows);
    }

    /** Các gói trong 1 danh mục: mỗi SP 1 hàng "Tên | 50k | 📦 tồn" hoặc "🚫 Hết hàng", cuối là Làm mới / Danh mục / Trang chủ. */
    public InlineKeyboardMarkup productList(String lang, long categoryId, List<ProductView> views) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (ProductView v : views) {
            Product p = v.product();
            String key = v.stock() > 0 ? "product.row" : "product.row_out";
            String label = msg.t(lang, key, p.label(), Formats.price(p.getPrice()), v.stock());
            rows.add(row(cb(label, CallbackData.of(CallbackData.PRODUCT, p.getId()))));
        }
        rows.add(row(btn(lang, "btn.refresh", CallbackData.of(CallbackData.CATEGORY, categoryId))));
        rows.add(row(btn(lang, "btn.categories", CallbackData.CATEGORIES), btn(lang, "btn.home", CallbackData.HOME)));
        return Buttons.markup(rows);
    }

    /** "Chọn sản phẩm khác" quay về đúng danh mục của SP đang xem. */
    public InlineKeyboardMarkup productDetail(String lang, long categoryId) {
        return Buttons.markup(
                row(btn(lang, "btn.other_product", CallbackData.of(CallbackData.CATEGORY, categoryId))),
                row(btn(lang, "btn.categories", CallbackData.CATEGORIES), btn(lang, "btn.home", CallbackData.HOME)));
    }

    InlineKeyboardButton btn(String lang, String key, String data) {
        return cb(msg.t(lang, key), data);
    }
}
