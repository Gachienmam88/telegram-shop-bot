package com.shopbot.service.crypto;

import java.util.List;

import com.shopbot.domain.CryptoNetwork;

/** Nguồn đọc giao dịch USDT đến ví shop trên 1 mạng, theo con trỏ (số block / timestamp). */
public interface UsdtTransferSource {

    CryptoNetwork network();

    /**
     * Đọc lô giao dịch tiếp theo sau con trỏ. cursor null = lần chạy đầu → chỉ lấy mốc hiện tại, không quét lịch sử.
     * Lỗi mạng/RPC → ném exception, con trỏ giữ nguyên để lần sau đọc lại.
     */
    Batch fetch(Long cursor);

    /** transfers: giao dịch đã đủ xác nhận; nextCursor: lưu lại cho lần sau; hasMore: còn lô cần đọc ngay. */
    record Batch(List<IncomingUsdtTransfer> transfers, long nextCursor, boolean hasMore) {
    }
}
