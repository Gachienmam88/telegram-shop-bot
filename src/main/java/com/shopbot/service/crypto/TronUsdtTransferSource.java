package com.shopbot.service.crypto;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopbot.domain.CryptoNetwork;

/**
 * TRON: đọc giao dịch TRC20 USDT đến ví qua TronGrid (only_confirmed=true → đã được xác nhận).
 * Con trỏ = block_timestamp (ms) mới nhất đã đọc; min_timestamp lấy cả mốc đó nên có thể đọc lại → chống trùng ở DB.
 */
public class TronUsdtTransferSource implements UsdtTransferSource {

    static final int PAGE_SIZE = 200;

    private final String address;
    private final RestClient trongrid;
    private final Clock clock;

    public TronUsdtTransferSource(String address, RestClient trongrid, Clock clock) {
        this.address = address;
        this.trongrid = trongrid;
        this.clock = clock;
    }

    @Override
    public CryptoNetwork network() {
        return CryptoNetwork.TRON;
    }

    @Override
    public Batch fetch(Long cursor) {
        if (cursor == null) {
            return new Batch(List.of(), clock.millis(), false);
        }
        JsonNode res = trongrid.get()
                .uri(b -> b.path("/v1/accounts/{address}/transactions/trc20")
                        .queryParam("only_to", true)
                        .queryParam("only_confirmed", true)
                        .queryParam("contract_address", CryptoNetwork.TRON.usdtContract())
                        .queryParam("order_by", "block_timestamp,asc")
                        .queryParam("min_timestamp", cursor)
                        .queryParam("limit", PAGE_SIZE)
                        .build(address))
                .retrieve()
                .body(JsonNode.class);
        if (res == null || !res.path("success").asBoolean(false)) {
            throw new IllegalStateException("TronGrid failed: " + res);
        }
        JsonNode data = res.path("data");
        long next = cursor;
        for (JsonNode tx : data) {
            next = Math.max(next, tx.path("block_timestamp").asLong(cursor));
        }
        // Trang đầy mà con trỏ không tiến (≥200 giao dịch cùng 1 mili giây) → bỏ qua mốc đó để không kẹt.
        boolean full = data.size() >= PAGE_SIZE;
        if (full && next == cursor) {
            next = cursor + 1;
        }
        return new Batch(parse(address, data), next, full);
    }

    /** Lọc đúng USDT chuyển đến ví (TronGrid có thể trả cả token khác nếu filter bị bỏ qua). */
    static List<IncomingUsdtTransfer> parse(String address, JsonNode data) {
        List<IncomingUsdtTransfer> out = new ArrayList<>();
        for (JsonNode tx : data) {
            if (!"Transfer".equals(tx.path("type").asText())
                    || !address.equals(tx.path("to").asText())
                    || !CryptoNetwork.TRON.usdtContract().equals(tx.path("token_info").path("address").asText())) {
                continue;
            }
            BigDecimal amount = new BigDecimal(tx.path("value").asText("0")).movePointLeft(CryptoNetwork.TRON.decimals());
            out.add(new IncomingUsdtTransfer(CryptoNetwork.TRON, tx.path("transaction_id").asText(), 0,
                    tx.path("from").asText(), amount));
        }
        return out;
    }
}
