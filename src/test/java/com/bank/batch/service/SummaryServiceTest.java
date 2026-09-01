package com.bank.batch.service;

import com.bank.batch.model.AnnualStatement;
import com.bank.batch.model.Transaction;
import com.bank.batch.repository.AnnualAccountSummaryRepository;
import com.bank.batch.repository.AnnualStatementRepository;
import com.bank.batch.repository.DailyTransactionSummaryRepository;
import com.bank.batch.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SummaryServiceTest {
    @TempDir
    Path outputDirectory;

    @Test
    void dailySummaryUsesAbsoluteAmountsForVolumes() throws Exception {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        DailyTransactionSummaryRepository dailyRepository = mock(DailyTransactionSummaryRepository.class);
        AnnualStatementRepository annualRepository = mock(AnnualStatementRepository.class);
        AnnualAccountSummaryRepository annualSummaryRepository = mock(AnnualAccountSummaryRepository.class);

        Transaction credit = transaction(1L, "credito", "-100.00");
        Transaction debit = transaction(2L, "debito", "-30.00");
        when(transactionRepository.findAll()).thenReturn(List.of(credit, debit));

        SummaryService service = new SummaryService(transactionRepository, dailyRepository,
                annualRepository, annualSummaryRepository, outputDirectory.toString());

        assertThat(service.rebuildDailySummary()).isEqualTo(1);
        assertThat(Files.readAllLines(outputDirectory.resolve("daily_transaction_summary.csv")))
                .containsExactly(
                        "fecha,total_transacciones,creditos,debitos,anomalias,total_creditos,total_debitos",
                        "2024-01-01,2,1,1,0,100.00,30.00");
    }

    @Test
    void annualNetIsDepositsMinusWithdrawalsRegardlessOfLegacySigns() throws Exception {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        DailyTransactionSummaryRepository dailyRepository = mock(DailyTransactionSummaryRepository.class);
        AnnualStatementRepository annualRepository = mock(AnnualStatementRepository.class);
        AnnualAccountSummaryRepository annualSummaryRepository = mock(AnnualAccountSummaryRepository.class);

        AnnualStatement deposit = statement("a", "deposito", "100.00");
        AnnualStatement withdrawal = statement("b", "retiro", "30.00");
        AnnualStatement purchase = statement("c", "compra", "-20.00");
        when(annualRepository.findAll()).thenReturn(List.of(deposit, withdrawal, purchase));

        SummaryService service = new SummaryService(transactionRepository, dailyRepository,
                annualRepository, annualSummaryRepository, outputDirectory.toString());

        assertThat(service.rebuildAnnualSummary()).isEqualTo(1);
        assertThat(Files.readAllLines(outputDirectory.resolve("annual_account_summary.csv")))
                .containsExactly(
                        "cuenta_id,anio,movimientos,anomalias,total_depositos,total_retiros,saldo_neto",
                        "101,2024,3,0,100.00,50.00,50.00");
    }

    private Transaction transaction(long id, String type, String amount) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setFecha(LocalDate.of(2024, 1, 1));
        transaction.setTipo(type);
        transaction.setMonto(new BigDecimal(amount));
        transaction.setIsAnomaly(false);
        return transaction;
    }

    private AnnualStatement statement(String key, String type, String amount) {
        AnnualStatement statement = new AnnualStatement();
        statement.setRecordKey(key);
        statement.setCuentaId(101L);
        statement.setFecha(LocalDate.of(2024, 1, 1));
        statement.setReportYear(2024);
        statement.setTransaccion(type);
        statement.setMonto(new BigDecimal(amount));
        statement.setIsAnomaly(false);
        return statement;
    }
}
