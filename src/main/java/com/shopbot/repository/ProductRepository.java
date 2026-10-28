package com.shopbot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopbot.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryIdOrderByIdAsc(Long categoryId);

    List<Product> findAllByOrderByIdAsc();

    List<Product> findByCategoryIdAndActiveTrueOrderByIdAsc(Long categoryId);

    /** SP đang bán, xếp theo thứ tự danh mục (sort_order) rồi id → SP cùng loại nằm cạnh nhau. */
    @Query("select p from Product p, Category c where p.categoryId = c.id and p.active = true"
            + " order by c.sortOrder, c.id, p.id")
    List<Product> findActiveOrderByCategory();

    @Modifying
    @Query("update Product p set p.sold = p.sold + :n where p.id = :id")
    void incrementSold(@Param("id") Long id, @Param("n") int n);
}
