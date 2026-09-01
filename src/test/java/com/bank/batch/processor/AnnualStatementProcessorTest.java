package com.bank.batch.processor;

import com.bank.batch.model.AnnualStatement;
import com.bank.batch.service.DuplicateTracker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualStatementProcessorTest {

    @Test
    void normalizesDescriptionTypeAndYear() {
        AnnualStatementProcessor processor = new AnnualStatementProcessor(new DuplicateTracker());
        AnnualStatement statement = statement("depósito", null);

        AnnualStatement result = processor.process(statement);

        assertThat(result.getTransaccion()).isEqualTo("deposito");
        assertThat(result.getDescripcion()).isEqualTo("SIN DESCRIPCION");
        assertThat(result.getReportYear()).isEqualTo(2024);
        assertThat(result.getIsAnomaly()).isTrue();
        assertThat(result.getRecordKey()).isNotBlank();
    }

    @Test
    void filtersExactDuplicate() {
        AnnualStatementProcessor processor = new AnnualStatementProcessor(new DuplicateTracker());
        processor.process(statement("deposito", "Ingreso"));

        assertThat(processor.process(statement("deposito", "Ingreso"))).isNull();
    }

    private AnnualStatement statement(String type, String description) {
        AnnualStatement statement = new AnnualStatement();
        statement.setCuentaId(101L);
        statement.setFecha(LocalDate.of(2024, 1, 1));
        statement.setTransaccion(type);
        statement.setMonto(new BigDecimal("1000"));
        statement.setDescripcion(description);
        return statement;
    }
}
