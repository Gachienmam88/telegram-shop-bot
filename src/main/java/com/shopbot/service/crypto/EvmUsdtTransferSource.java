package com.shopbot.service.crypto;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopbot.domain.CryptoNetwork;

/**
 * BSC / Polygon: đọc event Transfer(from, to=ví shop) của đúng contract USDT qua JSON-RPC eth_getLogs.
 * Chỉ đọc tới block (mới nhất − số xác nhận) để tránh giao dịch bị đảo khối.
 */
public class EvmUsdtTransferSource implements UsdtTransferSource {

    /** keccak256("Transfer(address,address,uint256)"). */
    static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    /** Số block tối đa mỗi lần eth_getLogs (node công khai giới hạn khoảng quét). */
    static final long CHUNK = 2000;

    private final CryptoNetwork network;
    private final String address;
    private final RestClient rpc;

    public EvmUsdtTransferSource(CryptoNetwork network, String address, RestClient rpc) {
        this.network = network;
        this.address = address.toLowerCase(Locale.ROOT);
        this.rpc = rpc;
    }

    @Override
    public CryptoNetwork network() {
        return network;
    }

    @Override
    public Batch fetch(Long cursor) {
        long safeHead = hexToLong(call("eth_blockNumber", List.of()).asText()) - network.confirmations();
        if (cursor == null) {
            return new Batch(List.of(), safeHead, false);
        }
        long from = cursor + 1;
        if (from > safeHead) {
            return new Batch(List.of(), cursor, false);
        }
        long to = Math.min(safeHead, from + CHUNK - 1);
        Map<String, Object> filter = Map.of(
                "fromBlock", toHex(from),
                "toBlock", toHex(to),
                "address", network.usdtContract(),
                "topics", java.util.Arrays.asList(TRANSFER_TOPIC, null, addressTopic(address)));
        JsonNode logs = call("eth_getLogs", List.of(filter));
        return new Batch(parseLogs(network, address, logs), to, to < safeHead);
    }

    /** Tách giao dịch USDT đến ví từ kết quả eth_getLogs (bỏ log bị đảo khối / sai contract / sai người nhận). */
    static List<IncomingUsdtTransfer> parseLogs(CryptoNetwork network, String address, JsonNode logs) {
        List<IncomingUsdtTransfer> out = new ArrayList<>();
        String expectedTo = addressTopic(address.toLowerCase(Locale.ROOT));
        for (JsonNode log : logs) {
            JsonNode topics = log.path("topics");
            if (log.path("removed").asBoolean(false)
                    || !network.usdtContract().equalsIgnoreCase(log.path("address").asText())
                    || topics.size() < 3
                    || !TRANSFER_TOPIC.equalsIgnoreCase(topics.get(0).asText())
                    || !expectedTo.equalsIgnoreCase(topics.get(2).asText())) {
                continue;
            }
            String data = log.path("data").asText("0x");
            BigInteger raw = data.length() > 2 ? new BigInteger(data.substring(2), 16) : BigInteger.ZERO;
            String from = "0x" + topics.get(1).asText().substring(26);
            out.add(new IncomingUsdtTransfer(network, log.path("transactionHash").asText(),
                    (int) hexToLong(log.path("logIndex").asText("0x0")), from,
                    new BigDecimal(raw, network.decimals())));
        }
        return out;
    }

    private JsonNode call(String method, List<?> params) {
        JsonNode res = rpc.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params))
                .retrieve()
                .body(JsonNode.class);
        if (res == null || res.has("error") || !res.has("result")) {
            throw new IllegalStateException(network + " RPC " + method + " failed: " + res);
        }
        return res.get("result");
    }

    /** Địa chỉ 20 byte → topic 32 byte (đệm 0 bên trái). */
    static String addressTopic(String address) {
        return "0x000000000000000000000000" + address.substring(2).toLowerCase(Locale.ROOT);
    }

    private static long hexToLong(String hex) {
        return Long.parseLong(hex.startsWith("0x") ? hex.substring(2) : hex, 16);
    }

    private static String toHex(long n) {
        return "0x" + Long.toHexString(n);
    }
}
