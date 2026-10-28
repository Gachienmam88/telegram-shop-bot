package com.shopbot.telegram.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.shopbot.domain.Category;
import com.shopbot.domain.Product;
import com.shopbot.service.CatalogAdminService;
import com.shopbot.service.StockService;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.telegram.handler.CatalogHandler;
import com.shopbot.util.Formats;

/** Lệnh admin quản lý danh mục và sản phẩm. */
@Component
public class AdminCatalogCommands implements AdminCommandSet {

    private final CatalogAdminService catalog;
    private final StockService stock;
    private final CatalogHandler catalogHandler;
    private final TelegramSender sender;

    public AdminCatalogCommands(CatalogAdminService catalog, StockService stock, CatalogHandler catalogHandler,
                                TelegramSender sender) {
        this.catalog = catalog;
        this.stock = stock;
        this.catalogHandler = catalogHandler;
        this.sender = sender;
    }

    @Override
    public Map<String, AdminCommand> commands() {
        Map<String, AdminCommand> m = new LinkedHashMap<>();
        m.put("/admin", r -> reply(r, AdminHelpText.TEXT));
        m.put("/addcat", this::addCategory);
        m.put("/cats", r -> reply(r, categoriesText()));
        m.put("/addproduct", this::addProduct);
        m.put("/plist", this::listProducts);
        m.put("/setdesc", this::setDescription);
        m.put("/setprice", r -> updateProduct(r, (p, v) -> p.setPrice(positivePrice(v)), "giá"));
        m.put("/setname", r -> updateProduct(r, (p, v) -> p.setName(v), "tên"));
        m.put("/hide", r -> toggle(r, false));
        m.put("/show", r -> toggle(r, true));
        return m;
    }

    /** /addcat 🎬 | CapCut [| thứ tự] */
    private void addCategory(AdminRequest r) {
        String[] a = r.pipeArgs();
        if (a.length < 2 || a[1].isBlank()) {
            throw new IllegalArgumentException("Cú pháp: /addcat emoji | Tên danh mục [| thứ tự]");
        }
        int order = a.length > 2 ? (int) AdminRequest.parseLong(a[2], "Thứ tự") : 0;
        Category c = catalog.addCategory(a[0], a[1], order);
        reply(r, "✅ Đã thêm danh mục #" + c.getId() + ": " + Formats.html(c.label()));
    }

    /** /addproduct cat_id | emoji | Tên | giá [⏎ mô tả nhiều dòng (HTML)]. Có mô tả thì xem trước, lỗi HTML thì không tạo. */
    private void addProduct(AdminRequest r) {
        String[] a = r.pipeArgs();
        if (a.length < 4) {
            throw new IllegalArgumentException("Cú pháp: /addproduct cat_id | emoji | Tên sản phẩm | giá ⏎ mô tả (tuỳ chọn)");
        }
        long categoryId = AdminRequest.parseLong(a[0], "cat_id");
        long price = AdminRequest.parseLong(a[3], "Giá");
        String description = r.body().strip();
        if (!description.isEmpty()) {
            Product preview = new Product();
            preview.setEmoji(a[1]);
            preview.setName(a[2]);
            preview.setPrice(price);
            preview.setDescription(description);
            sendPreview(r, preview, 0);
        }
        Product p = catalog.addProduct(categoryId, a[1], a[2], price, description);
        reply(r, "✅ Đã thêm sản phẩm #" + p.getId() + ": " + Formats.html(p.label()) + " — " + Formats.money(p.getPrice())
                + (description.isEmpty() ? " (chưa có mô tả)" : " (kèm mô tả, xem trước ở trên)")
                + "\nTiếp theo: " + (description.isEmpty() ? "/setdesc " + p.getId() + " (xuống dòng ghi mô tả), " : "")
                + "/addstock " + p.getId());
    }

    private void listProducts(AdminRequest r) {
        Long categoryId = r.args().isBlank() ? null : AdminRequest.parseLong(r.args(), "cat_id");
        List<Product> products = catalog.products(categoryId);
        if (products.isEmpty()) {
            reply(r, "Chưa có sản phẩm.");
            return;
        }
        String text = products.stream()
                .map(p -> "#" + p.getId() + " [cat " + p.getCategoryId() + "] " + Formats.html(p.label()) + " — "
                        + Formats.money(p.getPrice()) + " — tồn " + stock.available(p.getId()) + " — bán " + p.getSold()
                        + (p.isActive() ? "" : " (ẨN)"))
                .collect(Collectors.joining("\n"));
        reply(r, Formats.truncate(text, 4000));
    }

    /** /setdesc id + xuống dòng mô tả (HTML). Gửi thử bản xem trước, lỗi HTML thì không lưu. */
    private void setDescription(AdminRequest r) {
        long id = AdminRequest.parseLong(r.args(), "id");
        String description = r.body().strip();
        Product preview = catalog.product(id);
        preview.setDescription(description);
        sendPreview(r, preview, stock.available(id));
        catalog.update(id, p -> p.setDescription(description));
        reply(r, "✅ Đã lưu mô tả (xem trước ở trên).");
    }

    /** Gửi admin bản xem trước chi tiết SP như khách thấy; Telegram từ chối (HTML sai/quá dài) thì báo lỗi, không lưu. */
    private void sendPreview(AdminRequest r, Product preview, long available) {
        try {
            sender.sendOrThrow(String.valueOf(r.ctx().chatId()),
                    catalogHandler.detailText(r.ctx().lang(), preview, available), null);
        } catch (TelegramApiException e) {
            throw new IllegalArgumentException("Mô tả không hợp lệ (HTML sai hoặc quá dài), chưa lưu: " + e.getMessage());
        }
    }

    private void updateProduct(AdminRequest r, java.util.function.BiConsumer<Product, String> change, String field) {
        String[] a = r.args().split("\\s+", 2);
        if (a.length < 2 || a[1].isBlank()) {
            throw new IllegalArgumentException("Cú pháp: lệnh id giá_trị_mới");
        }
        Product p = catalog.update(AdminRequest.parseLong(a[0], "id"), x -> change.accept(x, a[1].trim()));
        reply(r, "✅ Đã cập nhật " + field + ": #" + p.getId() + " " + Formats.html(p.label()) + " — " + Formats.money(p.getPrice()));
    }

    private static long positivePrice(String value) {
        long price = AdminRequest.parseLong(value, "Giá");
        if (price <= 0) {
            throw new IllegalArgumentException("Giá phải > 0");
        }
        return price;
    }

    private void toggle(AdminRequest r, boolean active) {
        Product p = catalog.update(AdminRequest.parseLong(r.args(), "id"), x -> x.setActive(active));
        reply(r, (active ? "✅ Đã hiện " : "🙈 Đã ẩn ") + "#" + p.getId() + " " + Formats.html(p.label()));
    }

    private String categoriesText() {
        List<Category> cats = catalog.allCategories();
        if (cats.isEmpty()) {
            return "Chưa có danh mục. Thêm bằng: /addcat 🎬 | CapCut";
        }
        return cats.stream().map(c -> "#" + c.getId() + " " + Formats.html(c.label()) + " (thứ tự " + c.getSortOrder() + ")")
                .collect(Collectors.joining("\n"));
    }

    private void reply(AdminRequest r, String html) {
        sender.send(r.ctx().chatId(), html, null);
    }
}
