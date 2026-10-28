package com.shopbot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.shopbot.domain.Topup;
import com.shopbot.service.TopupService;
import com.shopbot.support.PostgresTestBase;

@AutoConfigureMockMvc
class SepayWebhookControllerTest extends PostgresTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    TopupService topups;

    private static String body(long id, String type, long amount, String content) {
        return """
                {"id": %d, "gateway": "MBBank", "transactionDate": "2026-10-14 10:00:00",
                 "accountNumber": "0366189136", "code": null, "content": "%s", "transferType": "%s",
                 "transferAmount": %d, "accumulated": 0, "referenceCode": "FT1", "description": ""}
                """.formatted(id, content, type, amount);
    }

    @Test
    void rejectsMissingOrWrongApiKey() throws Exception {
        mvc.perform(post("/sepay/webhook").contentType(MediaType.APPLICATION_JSON).content(body(1, "in", 1000, "x")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/sepay/webhook").header("Authorization", "Apikey wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body(1, "in", 1000, "x")))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_transactions", Integer.class)).isZero();
    }

    @Test
    void creditsWalletForIncomingTopupTransfer() throws Exception {
        createUser(100, 0);
        Topup t = topups.create(100, 100, 100_000);

        mvc.perform(post("/sepay/webhook").header("Authorization", "Apikey test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body(42, "in", 100_000, "CK " + t.getPayRef())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(balanceOf(100)).isEqualTo(100_000);
    }

    @Test
    void ignoresOutgoingTransfers() throws Exception {
        createUser(100, 0);
        Topup t = topups.create(100, 100, 100_000);

        mvc.perform(post("/sepay/webhook").header("Authorization", "Apikey test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body(43, "out", 100_000, t.getPayRef())))
                .andExpect(status().isOk());

        assertThat(balanceOf(100)).isZero();
    }
}
