package com.bank.batch.mapper;

import com.bank.batch.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlexibleLocalDateParserTest {
    private final FlexibleLocalDateParser parser = new FlexibleLocalDateParser();

    @Test
    void parsesEverySupportedLegacyFormat() {
        LocalDate expected = LocalDate.of(2024, 4, 3);

        assertThat(parser.parse("2024-04-03")).isEqualTo(expected);
        assertThat(parser.parse("2024/04/03")).isEqualTo(expected);
        assertThat(parser.parse("03-04-2024")).isEqualTo(expected);
        assertThat(parser.parse("03/04/2024")).isEqualTo(expected);
    }

    @Test
    void rejectsImpossibleDate() {
        assertThatThrownBy(() -> parser.parse("2024-13-01"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Formato de fecha no soportado");
    }
}

