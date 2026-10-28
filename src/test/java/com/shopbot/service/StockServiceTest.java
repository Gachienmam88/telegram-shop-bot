package com.shopbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shopbot.support.PostgresTestBase;

class StockServiceTest extends PostgresTestBase {

    @Autowired
    StockService stock;

    @Test
    void skipsBlankAndDuplicateLines() {
        long productId = createProduct(100_000, 1); // đã có "acc1|pass1"

        StockService.AddResult r = stock.addStock(productId,
                List.of("acc1|pass1", "new1|p", "", "  ", "new2|p", "new1|p", " new3|p "));

        assertThat(r.added()).isEqualTo(3);
        assertThat(r.duplicates()).isEqualTo(2);
        assertThat(r.currentStock()).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT content FROM stock_items ORDER BY id", String.class))
                .containsExactly("acc1|pass1", "new1|p", "new2|p", "new3|p");
    }

    @Test
    void softDeleteKeepsRowsAndOnlyTouchesAvailableStock() {
        long productId = createProduct(100_000, 3);
        jdbc.update("UPDATE stock_items SET status = 'SOLD' WHERE content = 'acc3|pass3'");

        long[] one = stock.softDelete(productId, List.of("acc1|pass1"));
        assertThat(one).containsExactly(1, 1);

        long[] all = stock.softDelete(productId, List.of());
        assertThat(all).containsExactly(1, 0);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_items", Integer.class)).as("không xóa cứng").isEqualTo(3);
        assertThat(stockCount(productId, "DELETED")).isEqualTo(2);
        assertThat(stockCount(productId, "SOLD")).as("hàng đã bán giữ nguyên").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_items WHERE status = 'DELETED' AND deleted_at IS NOT NULL",
                Integer.class)).isEqualTo(2);

        // Tài khoản đã xóa mềm được phép nạp lại.
        assertThat(stock.addStock(productId, List.of("acc1|pass1")).added()).isEqualTo(1);
    }
}
