package com.shopbot.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormatsTest {

    @Test
    void priceUsesShortThousandsFormat() {
        assertThat(Formats.price(50_000)).isEqualTo("50k");
        assertThat(Formats.price(450_000)).isEqualTo("450k");
        assertThat(Formats.price(1_650_000)).isEqualTo("1650k");
        assertThat(Formats.price(12_345)).as("không chẵn nghìn thì ghi đủ").isEqualTo("12.345đ");
    }

    @Test
    void moneyKeepsFullFormatForTransfers() {
        assertThat(Formats.money(50_000)).isEqualTo("50.000đ");
    }
}
