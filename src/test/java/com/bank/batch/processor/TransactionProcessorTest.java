package com.bank.batch.processor;

import com.bank.batch.model.Transaction;
import com.bank.batch.service.DuplicateTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionProcessorTest {
    private DuplicateTracker tracker;
    private TransactionProcessor processor;

    @BeforeEach
    void setUp() {
        tracker = new DuplicateTracker();
        processor = new TransactionProcessor(tracker);
    }

    @Test
    void accumulatesAllAnomalyReasons() {
        Transaction transaction = transaction(1L, new BigDecimal("-10"), "invalid");

        Transaction result = processor.process(transaction);

        assertThat(result.getIsAnomaly()).isTrue();
        assertThat(result.getErrorReason()).isEqualTo("MONTO_NO_POSITIVO;TIPO_TRANSACCION_INVALIDO");
    }

    @Test
    void filtersOnlyExactDuplicatesAndKeepsDifferentTransactionsWithSameLegacyId() {
        processor.process(transaction(1L, new BigDecimal("100"), "credito"));

        assertThat(processor.process(transaction(1L, new BigDecimal("100"), "credito"))).isNull();
        assertThat(processor.process(transaction(1L, new BigDecimal("200"), "debito"))).isNotNull();
    }

    private Transaction transaction(Long id, BigDecimal amount, String type) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setFecha(LocalDate.of(2024, 1, 1));
        transaction.setMonto(amount);
        transaction.setTipo(type);
        return transaction;
    }
}
