package com.shopbot.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.Product;
import com.shopbot.domain.StockStatus;
import com.shopbot.repository.ProductRepository;
import com.shopbot.repository.StockItemRepository;

/** Admin nạp tài khoản vào kho. */
@Service
public class StockService {

    public static final int MAX_LINES_PER_BATCH = 10_000;

    /** Kết quả nạp kho. */
    public record AddResult(Product product, int added, int duplicates, long currentStock) {
    }

    private final StockItemRepository stock;
    private final ProductRepository products;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public StockService(StockItemRepository stock, ProductRepository products, JdbcTemplate jdbc, Clock clock) {
        this.stock = stock;
        this.products = products;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Thêm mỗi dòng 1 tài khoản. Bỏ dòng trống, dòng trùng trong batch và trùng với kho hiện có. */
    @Transactional
    public AddResult addStock(long productId, List<String> rawLines) {
        Product p = products.findById(productId).orElseThrow(() -> new ShopException("product.not_found"));
        Set<String> unique = new LinkedHashSet<>();
        int nonBlank = 0;
        for (String line : rawLines) {
            String s = line.strip();
            if (!s.isEmpty()) {
                nonBlank++;
                unique.add(s);
            }
        }
        if (nonBlank > MAX_LINES_PER_BATCH) {
            throw new IllegalArgumentException("Tối đa " + MAX_LINES_PER_BATCH + " dòng mỗi lần");
        }
        Set<String> existing = new HashSet<>(stock.findContentsByProductId(productId));
        List<String> toInsert = new ArrayList<>();
        for (String s : unique) {
            if (!existing.contains(s)) {
                toInsert.add(s);
            }
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.batchUpdate("INSERT INTO stock_items (product_id, content, status, created_at) VALUES (?, ?, ?, ?)",
                toInsert, 500, (ps, content) -> {
                    ps.setLong(1, productId);
                    ps.setString(2, content);
                    ps.setString(3, StockStatus.AVAILABLE.name());
                    ps.setTimestamp(4, now);
                });
        long current = stock.countByProductIdAndStatus(productId, StockStatus.AVAILABLE);
        return new AddResult(p, toInsert.size(), nonBlank - toInsert.size(), current);
    }

    /**
     * Xóa mềm tài khoản còn trống: lines rỗng → xóa hết hàng còn trống của SP, ngược lại chỉ xóa các dòng chỉ định.
     * Trả về [số đã xóa, tồn kho còn lại].
     */
    @Transactional
    public long[] softDelete(long productId, List<String> lines) {
        products.findById(productId).orElseThrow(() -> new ShopException("product.not_found"));
        Set<String> targets = new LinkedHashSet<>();
        lines.stream().map(String::strip).filter(s -> !s.isEmpty()).forEach(targets::add);
        int deleted = targets.isEmpty()
                ? stock.softDeleteAvailable(productId, clock.instant())
                : stock.softDeleteAvailableContents(productId, targets, clock.instant());
        return new long[] {deleted, stock.countByProductIdAndStatus(productId, StockStatus.AVAILABLE)};
    }

    @Transactional(readOnly = true)
    public long available(long productId) {
        return stock.countByProductIdAndStatus(productId, StockStatus.AVAILABLE);
    }
}
