package com.shopbot.service.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopbot.domain.CryptoNetwork;

/** Đọc đúng số tiền / bỏ qua log giả mạo từ dữ liệu RPC (BSC) và TronGrid. */
class UsdtTransferParsingTest {

    private static final String SHOP_EVM = "0x5ab0000000000000000000000000000000000001";
    private static final String SHOP_TRON = "TJEif21DCjR1HwfdFK48Mk3XWzfoDHpTYX";
    private final ObjectMapper json = new ObjectMapper();

    private String evmLog(String contract, String toTopic, String dataHex, boolean removed) {
        return """
                {"address":"%s","removed":%s,"logIndex":"0x1f","transactionHash":"0xabc",
                 "data":"%s",
                 "topics":["%s","0x000000000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","%s"]}
                """.formatted(contract, removed, dataHex, EvmUsdtTransferSource.TRANSFER_TOPIC, toTopic);
    }

    @Test
    void parsesBscTransferWith18Decimals() throws Exception {
        String to = EvmUsdtTransferSource.addressTopic(SHOP_EVM);
        // 3.7137 USDT × 10^18 = 3713700000000000000 = 0x3389b679565e4000
        String good = evmLog(CryptoNetwork.BSC.usdtContract().toLowerCase(), to, "0x3389b679565e4000", false);
        String fakeToken = evmLog("0x1111111111111111111111111111111111111111", to, "0x3389b679565e4000", false);
        String reorged = evmLog(CryptoNetwork.BSC.usdtContract(), to, "0x3389b679565e4000", true);
        JsonNode logs = json.readTree("[" + good + "," + fakeToken + "," + reorged + "]");

        List<IncomingUsdtTransfer> out = EvmUsdtTransferSource.parseLogs(CryptoNetwork.BSC, SHOP_EVM, logs);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).amount()).isEqualByComparingTo(new BigDecimal("3.7137"));
        assertThat(out.get(0).logIndex()).isEqualTo(31);
        assertThat(out.get(0).from()).isEqualTo("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(out.get(0).key()).isEqualTo("BSC:0xabc:31");
    }

    @Test
    void parsesTronUsdtOnlyToShopAddress() throws Exception {
        String c = CryptoNetwork.TRON.usdtContract();
        JsonNode data = json.readTree("""
                [{"transaction_id":"t1","type":"Transfer","from":"TX","to":"%s","value":"10000500",
                  "token_info":{"address":"%s","decimals":6}},
                 {"transaction_id":"t2","type":"Transfer","from":"TX","to":"%s","value":"10000500",
                  "token_info":{"address":"TFakeTokenxxxxxxxxxxxxxxxxxxxxxxxx","decimals":6}},
                 {"transaction_id":"t3","type":"Transfer","from":"%s","to":"TOther","value":"10000500",
                  "token_info":{"address":"%s","decimals":6}}]
                """.formatted(SHOP_TRON, c, SHOP_TRON, SHOP_TRON, c));

        List<IncomingUsdtTransfer> out = TronUsdtTransferSource.parse(SHOP_TRON, data);

        assertThat(out).extracting(IncomingUsdtTransfer::txHash).containsExactly("t1");
        assertThat(out.get(0).amount()).isEqualByComparingTo(new BigDecimal("10.0005"));
    }
}
