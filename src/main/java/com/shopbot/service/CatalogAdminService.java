package com.shopbot.service;

import java.time.Clock;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.Category;
import com.shopbot.domain.Product;
import com.shopbot.repository.CategoryRepository;
import com.shopbot.repository.ProductRepository;

/** Admin thêm/sửa danh mục và sản phẩm. */
@Service
public class CatalogAdminService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final Clock clock;

    public CatalogAdminService(CategoryRepository categories, ProductRepository products, Clock clock) {
        this.categories = categories;
        this.products = products;
        this.clock = clock;
    }

    @Transactional
    public Category addCategory(String emoji, String name, int sortOrder) {
        Category c = new Category();
        c.setEmoji(emoji);
        c.setName(name);
        c.setSortOrder(sortOrder);
        return categories.save(c);
    }

    @Transactional(readOnly = true)
    public List<Category> allCategories() {
        return categories.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional
    public Product addProduct(long categoryId, String emoji, String name, long price, String description) {
        if (!categories.existsById(categoryId)) {
            throw new IllegalArgumentException("Không có danh mục id " + categoryId);
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Giá phải > 0");
        }
        Product p = new Product();
        p.setCategoryId(categoryId);
        p.setEmoji(emoji);
        p.setName(name);
        p.setPrice(price);
        p.setDescription(description);
        p.setCreatedAt(clock.instant());
        return products.save(p);
    }

    @Transactional(readOnly = true)
    public List<Product> products(Long categoryId) {
        return categoryId == null ? products.findAllByOrderByIdAsc() : products.findByCategoryIdOrderByIdAsc(categoryId);
    }

    @Transactional(readOnly = true)
    public Product product(long id) {
        return products.findById(id).orElseThrow(() -> new IllegalArgumentException("Không có sản phẩm id " + id));
    }

    /** Sửa 1 SP bằng hàm cập nhật (đổi giá, tên, mô tả, ẩn/hiện...). */
    @Transactional
    public Product update(long id, Consumer<Product> change) {
        Product p = product(id);
        change.accept(p);
        return products.save(p);
    }
}
