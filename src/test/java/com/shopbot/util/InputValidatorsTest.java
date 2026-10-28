package com.shopbot.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class InputValidatorsTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "", "  ", "2a", "1e3", "１"})
    void quantityRejectsInvalidInput(String input) {
        assertThat(InputValidators.quantity(input, 10).status()).isEqualTo(InputValidators.Status.INVALID);
    }

    @ParameterizedTest
    @CsvSource({"1,1", "' 3 ',3", "10,10"})
    void quantityAcceptsValidInput(String input, long expected) {
        InputValidators.Result r = InputValidators.quantity(input, 10);
        assertThat(r.ok()).isTrue();
        assertThat(r.value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"11", "999999"})
    void quantityAboveStockIsOutOfRange(String input) {
        assertThat(InputValidators.quantity(input, 10).status()).isEqualTo(InputValidators.Status.OUT_OF_RANGE);
    }

    @ParameterizedTest
    @CsvSource({"100000,100000", "100.000,100000", "'100,000',100000", "'1 000 000',1000000"})
    void amountAcceptsSeparators(String input, long expected) {
        InputValidators.Result r = InputValidators.amount(input, 10_000, 50_000_000);
        assertThat(r.ok()).isTrue();
        assertThat(r.value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-5000", "10k", "1.5.0a"})
    void amountRejectsInvalid(String input) {
        assertThat(InputValidators.amount(input, 10_000, 50_000_000).status()).isEqualTo(InputValidators.Status.INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9999", "50000001"})
    void amountOutOfRange(String input) {
        assertThat(InputValidators.amount(input, 10_000, 50_000_000).status())
                .isEqualTo(InputValidators.Status.OUT_OF_RANGE);
    }
}
