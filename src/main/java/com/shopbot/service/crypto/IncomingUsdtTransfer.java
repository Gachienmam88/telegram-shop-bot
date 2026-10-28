package com.shopbot.service.crypto;

import java.math.BigDecimal;

import com.shopbot.domain.CryptoNetwork;

/** 1 lần chuyển USDT vào ví shop đã đủ xác nhận, đọc từ blockchain. */
public record IncomingUsdtTransfer(CryptoNetwork network, String txHash, int logIndex, String from,
                                   BigDecimal amount) {

    /** Khóa chống trùng: cùng giao dịch đọc lại nhiều lần chỉ xử lý 1 lần. */
    public String key() {
        return network.name() + ":" + txHash.toLowerCase() + ":" + logIndex;
    }
}
