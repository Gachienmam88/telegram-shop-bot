package com.shopbot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopbot.domain.StockItem;
import com.shopbot.domain.StockStatus;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    /**
     * Khóa N tài khoản còn trống. SKIP LOCKED: 2 người mua cùng lúc sẽ không lấy trùng
     * tài khoản, người sau chỉ thấy phần còn lại.
     */
    @Query(value = """
            SELECT * FROM stock_items
            WHERE product_id = :productId AND status = 'AVAILABLE'
            ORDER BY id
            LIMIT :n
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<StockItem> lockAvailable(@Param("productId") Long productId, @Param("n") int n);

    long countByProductIdAndStatus(Long productId, StockStatus status);

    /** [productId, số tài khoản còn trống]. */
    @Query("select s.productId, count(s) from StockItem s where s.status = :status group by s.productId")
    List<Object[]> countByStatusGrouped(@Param("status") StockStatus status);

    List<StockItem> findByOrderCodeOrderByIdAsc(String orderCode);

    @Modifying
    @Query("update StockItem s set s.status = :to where s.orderCode = :code and s.status = :from")
    int changeStatusByOrder(@Param("code") String code, @Param("from") StockStatus from, @Param("to") StockStatus to);

    @Modifying
    @Query("""
            update StockItem s set s.status = com.shopbot.domain.StockStatus.AVAILABLE, s.orderCode = null
            where s.orderCode = :code and s.status = com.shopbot.domain.StockStatus.RESERVED""")
    int releaseReserved(@Param("code") String code);

    /** Nội dung các tài khoản chưa bị xóa mềm (dùng để chống nạp trùng). */
    @Query("select s.content from StockItem s where s.productId = :productId"
            + " and s.status <> com.shopbot.domain.StockStatus.DELETED")
    List<String> findContentsByProductId(@Param("productId") Long productId);

    /** Xóa mềm toàn bộ tài khoản còn trống của SP. Không đụng tới hàng đang giữ / đã bán. */
    @Modifying
    @Query("""
            update StockItem s set s.status = com.shopbot.domain.StockStatus.DELETED, s.deletedAt = :now
            where s.productId = :productId and s.status = com.shopbot.domain.StockStatus.AVAILABLE""")
    int softDeleteAvailable(@Param("productId") Long productId, @Param("now") java.time.Instant now);

    /** Xóa mềm các tài khoản còn trống có nội dung nằm trong danh sách. */
    @Modifying
    @Query("""
            update StockItem s set s.status = com.shopbot.domain.StockStatus.DELETED, s.deletedAt = :now
            where s.productId = :productId and s.status = com.shopbot.domain.StockStatus.AVAILABLE
            and s.content in :contents""")
    int softDeleteAvailableContents(@Param("productId") Long productId,
                                    @Param("contents") java.util.Collection<String> contents,
                                    @Param("now") java.time.Instant now);
}
