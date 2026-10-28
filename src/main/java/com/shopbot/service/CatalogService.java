package com.shopbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.Category;
import com.shopbot.domain.Product;
import com.shopbot.domain.StockStatus;
import com.shopbot.repository.CategoryRepository;
import com.shopbot.repository.ProductRepository;
import com.shopbot.repository.StockItemRepository;

/** Đọc sản phẩm / tồn kho để hiển thị cho khách. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    /** SP kèm số tồn kho. */
    public record ProductView(Product product, long stock) {
    }

    /** Danh mục kèm tổng tồn kho (số tài khoản còn) của mọi SP đang bán trong đó. */
    public record CategoryView(Category category, long stock) {
    }

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final StockItemRepository stock;

    public CatalogService(ProductRepository products, CategoryRepository categories, StockItemRepository stock) {
        this.products = products;
        this.categories = categories;
        this.stock = stock;
    }

    /** Danh mục có ít nhất 1 SP đang bán (danh mục rỗng ẩn với khách), xếp theo sort_order rồi id. */
    public List<CategoryView> activeCategories() {
        Map<Long, Long> stockMap = availableStockByProduct();
        Map<Long, Long> stockByCategory = products.findActiveOrderByCategory().stream()
                .collect(Collectors.groupingBy(Product::getCategoryId,
                        Collectors.summingLong(p -> stockMap.getOrDefault(p.getId(), 0L))));
        return categories.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(c -> stockByCategory.containsKey(c.getId()))
                .map(c -> new CategoryView(c, stockByCategory.get(c.getId())))
                .toList();
    }

    public Optional<Category> category(long id) {
        return categories.findById(id);
    }

    /** SP đang bán trong 1 danh mục, theo id, kèm tồn kho. */
    public List<ProductView> activeProducts(long categoryId) {
        Map<Long, Long> stockMap = availableStockByProduct();
        return products.findByCategoryIdAndActiveTrueOrderByIdAsc(categoryId).stream()
                .map(p -> new ProductView(p, stockMap.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    /** product_id → số tài khoản AVAILABLE. */
    private Map<Long, Long> availableStockByProduct() {
        Map<Long, Long> stockMap = new HashMap<>();
        for (Object[] row : stock.countByStatusGrouped(StockStatus.AVAILABLE)) {
            stockMap.put((Long) row[0], (Long) row[1]);
        }
        return stockMap;
    }

    /** SP đang bán (ẩn → coi như không tồn tại với khách). */
    public Product activeProduct(long id) {
        return products.findById(id).filter(Product::isActive)
                .orElseThrow(() -> new ShopException("product.not_found"));
    }

    public int availableStock(long productId) {
        return (int) stock.countByProductIdAndStatus(productId, StockStatus.AVAILABLE);
    }
}
