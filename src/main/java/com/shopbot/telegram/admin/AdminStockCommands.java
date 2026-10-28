package com.shopbot.telegram.admin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Document;

import com.shopbot.service.StockService;
import com.shopbot.telegram.RestockNotifier;
import com.shopbot.telegram.TelegramSender;
import com.shopbot.util.Formats;

/** Lệnh admin nạp kho và xem tồn kho. */
@Component
public class AdminStockCommands implements AdminCommandSet {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final StockService stock;
    private final RestockNotifier restockNotifier;
    private final TelegramSender sender;

    public AdminStockCommands(StockService stock, RestockNotifier restockNotifier, TelegramSender sender) {
        this.stock = stock;
        this.restockNotifier = restockNotifier;
        this.sender = sender;
    }

    @Override
    public Map<String, AdminCommand> commands() {
        return Map.of(
                "/addstock", this::addStock,
                "/delstock", this::deleteStock,
                "/stock", r -> {
                    long id = AdminRequest.parseLong(r.args(), "id");
                    sender.send(r.ctx().chatId(), "📦 Tồn kho #" + id + ": " + stock.available(id), null);
                });
    }

    /** /addstock id + các dòng tài khoản, hoặc file .txt có caption "/addstock id". */
    private void addStock(AdminRequest r) throws Exception {
        if (r.args().isBlank()) {
            throw new IllegalArgumentException("Cú pháp: /addstock id rồi xuống dòng, mỗi dòng 1 tài khoản");
        }
        long productId = AdminRequest.parseLong(r.words()[0], "id");
        List<String> lines = r.message().hasDocument() ? readDocument(r.message().getDocument()) : r.body().lines().toList();
        if (lines.stream().allMatch(String::isBlank)) {
            throw new IllegalArgumentException("Không có tài khoản nào để thêm");
        }
        StockService.AddResult result = stock.addStock(productId, lines);
        sender.send(r.ctx().chatId(), "✅ " + Formats.html(result.product().label())
                + "\n➕ Đã thêm: " + result.added()
                + "\n♻️ Bỏ qua (trùng): " + result.duplicates()
                + "\n📦 Tồn kho hiện tại: " + result.currentStock()
                + (result.added() > 0 ? "\n📢 Đang gửi thông báo hàng về..." : ""), null);
        restockNotifier.announce(result);
    }

    /** /delstock id → xóa mềm toàn bộ hàng còn trống; /delstock id + các dòng → chỉ xóa các tài khoản đó. */
    private void deleteStock(AdminRequest r) throws Exception {
        if (r.args().isBlank()) {
            throw new IllegalArgumentException("Cú pháp: /delstock id (xóa hết hàng còn trống) hoặc /delstock id + mỗi dòng 1 tài khoản");
        }
        long productId = AdminRequest.parseLong(r.words()[0], "id");
        List<String> lines = r.message().hasDocument() ? readDocument(r.message().getDocument()) : r.body().lines().toList();
        long[] result = stock.softDelete(productId, lines);
        sender.send(r.ctx().chatId(), "🗑 Đã xóa (soft delete): " + result[0]
                + "\n📦 Tồn kho còn lại: " + result[1]
                + "\n<i>Chỉ xóa hàng còn trống, không ảnh hưởng đơn đang chờ / đã bán.</i>", null);
    }

    private List<String> readDocument(Document doc) throws Exception {
        if (doc.getFileSize() != null && doc.getFileSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File quá lớn (tối đa 5MB)");
        }
        try (InputStream in = sender.download(doc.getFileId());
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            // Bỏ BOM nếu file lưu từ Notepad.
            return reader.lines().map(l -> l.replace(String.valueOf((char) 0xFEFF), "")).toList();
        }
    }
}
