package com.shopbot.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PayRefExtractorTest {

    @Test
    void findsRefInsideBankNoise() {
        assertThat(PayRefExtractor.find("MBVCB.123456.NGUYEN VAN A chuyen tien DH8K2P9QXA FT2601"))
                .contains("DH8K2P9QXA");
        assertThat(PayRefExtractor.find("nap8k2p9qxa")).contains("NAP8K2P9QXA");
    }

    @Test
    void emptyWhenNoRef() {
        assertThat(PayRefExtractor.find("chuyen tien an trua")).isEmpty();
        assertThat(PayRefExtractor.find(null)).isEmpty();
        assertThat(PayRefExtractor.find("DH123")).isEmpty();
    }
}
