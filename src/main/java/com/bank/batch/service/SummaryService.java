package com.bank.batch.service;

import com.bank.batch.model.AnnualAccountSummary;
import com.bank.batch.model.AnnualStatement;
import com.bank.batch.model.DailyTransactionSummary;
import com.bank.batch.model.Transaction;
import com.bank.batch.repository.AnnualAccountSummaryRepository;
import com.bank.batch.repository.AnnualStatementRepository;
import com.bank.batch.repository.DailyTransactionSummaryRepository;
import com.bank.batch.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SummaryService {
    private static final Logger logger = LoggerFactory.getLogger(SummaryService.class);

    private final TransactionRepository transactionRepository;
    private final DailyTransactionSummaryRepository dailySummaryRepository;
    private final AnnualStatementRepository annualStatementRepository;
    private final AnnualAccountSummaryRepository annualSummaryRepository;
    private final Path outputDirectory;

    public SummaryService(TransactionRepository transactionRepository,
                          DailyTransactionSummaryRepository dailySummaryRepository,
                          AnnualStatementRepository annualStatementRepository,
                          AnnualAccountSummaryRepository annualSummaryRepository,
                          @Value("${bank.batch.output-directory:output}") String outputDirectory) {
        this.transactionRepository = transactionRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.annualStatementRepository = annualStatementRepository;
        this.annualSummaryRepository = annualSummaryRepository;
        this.outputDirectory = Path.of(outputDirectory);
    }

    @Transactional
    public int rebuildDailySummary() throws IOException {
        Map<java.time.LocalDate, DailyAccumulator> accumulators = new LinkedHashMap<>();
        transactionRepository.findAll().stream()
                .sorted(Comparator.comparing(Transaction::getFecha)
                        .thenComparing(Transaction::getId)
                        .thenComparing(Transaction::getRecordKey))
                .forEach(transaction -> accumulators
                        .computeIfAbsent(transaction.getFecha(), ignored -> new DailyAccumulator())
                        .accept(transaction));

        List<DailyTransactionSummary> summaries = accumulators.entrySet().stream()
                .map(entry -> entry.getValue().toEntity(entry.getKey()))
                .toList();
        dailySummaryRepository.deleteAllInBatch();
        dailySummaryRepository.saveAll(summaries);
        writeDailyCsv(summaries);
        logger.info("RESUMEN_DIARIO_GENERADO fechas={} archivo={}", summaries.size(),
                outputDirectory.resolve("daily_transaction_summary.csv").toAbsolutePath());
        return summaries.size();
    }

    @Transactional
    public int rebuildAnnualSummary() throws IOException {
        Map<String, AnnualAccumulator> accumulators = new LinkedHashMap<>();
        annualStatementRepository.findAll().stream()
                .sorted(Comparator.comparing(AnnualStatement::getCuentaId)
                        .thenComparing(AnnualStatement::getFecha)
                        .thenComparing(AnnualStatement::getRecordKey))
                .forEach(statement -> {
                    String key = statement.getCuentaId() + "-" + statement.getReportYear();
                    accumulators.computeIfAbsent(key, ignored -> new AnnualAccumulator()).accept(statement);
                });

        List<AnnualAccountSummary> summaries = accumulators.entrySet().stream()
                .map(entry -> entry.getValue().toEntity(entry.getKey()))
                .toList();
        annualSummaryRepository.deleteAllInBatch();
        annualSummaryRepository.saveAll(summaries);
        writeAnnualCsv(summaries);
        logger.info("INFORME_ANUAL_GENERADO cuentas_anio={} archivo={}", summaries.size(),
                outputDirectory.resolve("annual_account_summary.csv").toAbsolutePath());
        return summaries.size();
    }

    private void writeDailyCsv(List<DailyTransactionSummary> summaries) throws IOException {
        Files.createDirectories(outputDirectory);
        List<String> lines = new ArrayList<>();
        lines.add("fecha,total_transacciones,creditos,debitos,anomalias,total_creditos,total_debitos");
        for (DailyTransactionSummary summary : summaries) {
            lines.add(String.join(",",
                    summary.getReportDate().toString(),
                    Long.toString(summary.getTotalTransactions()),
                    Long.toString(summary.getCreditCount()),
                    Long.toString(summary.getDebitCount()),
                    Long.toString(summary.getAnomalyCount()),
                    money(summary.getTotalCredits()),
                    money(summary.getTotalDebits())));
        }
        Files.write(outputDirectory.resolve("daily_transaction_summary.csv"), lines, StandardCharsets.UTF_8);
    }

    private void writeAnnualCsv(List<AnnualAccountSummary> summaries) throws IOException {
        Files.createDirectories(outputDirectory);
        List<String> lines = new ArrayList<>();
        lines.add("cuenta_id,anio,movimientos,anomalias,total_depositos,total_retiros,saldo_neto");
        for (AnnualAccountSummary summary : summaries) {
            lines.add(String.join(",",
                    summary.getCuentaId().toString(),
                    summary.getReportYear().toString(),
                    Long.toString(summary.getTransactionCount()),
                    Long.toString(summary.getAnomalyCount()),
                    money(summary.getTotalDeposits()),
                    money(summary.getTotalWithdrawals()),
                    money(summary.getNetBalance())));
        }
        Files.write(outputDirectory.resolve("annual_account_summary.csv"), lines, StandardCharsets.UTF_8);
    }

    private String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static final class DailyAccumulator {
        private long total;
        private long credits;
        private long debits;
        private long anomalies;
        private BigDecimal totalCredits = BigDecimal.ZERO;
        private BigDecimal totalDebits = BigDecimal.ZERO;

        void accept(Transaction transaction) {
            total++;
            if (Boolean.TRUE.equals(transaction.getIsAnomaly())) {
                anomalies++;
            }
            if ("credito".equals(transaction.getTipo())) {
                credits++;
                totalCredits = totalCredits.add(transaction.getMonto().abs());
            } else if ("debito".equals(transaction.getTipo())) {
                debits++;
                totalDebits = totalDebits.add(transaction.getMonto().abs());
            }
        }

        DailyTransactionSummary toEntity(java.time.LocalDate date) {
            DailyTransactionSummary summary = new DailyTransactionSummary();
            summary.setReportDate(date);
            summary.setTotalTransactions(total);
            summary.setCreditCount(credits);
            summary.setDebitCount(debits);
            summary.setAnomalyCount(anomalies);
            summary.setTotalCredits(totalCredits);
            summary.setTotalDebits(totalDebits);
            return summary;
        }
    }

    private static final class AnnualAccumulator {
        private Long accountId;
        private Integer reportYear;
        private long transactions;
        private long anomalies;
        private BigDecimal deposits = BigDecimal.ZERO;
        private BigDecimal withdrawals = BigDecimal.ZERO;
        void accept(AnnualStatement statement) {
            accountId = statement.getCuentaId();
            reportYear = statement.getReportYear();
            transactions++;
            if (Boolean.TRUE.equals(statement.getIsAnomaly())) {
                anomalies++;
            }
            BigDecimal amount = statement.getMonto().abs();
            if ("deposito".equals(statement.getTransaccion())) {
                deposits = deposits.add(amount);
            } else {
                withdrawals = withdrawals.add(amount);
            }
        }

        AnnualAccountSummary toEntity(String key) {
            AnnualAccountSummary summary = new AnnualAccountSummary();
            summary.setId(key);
            summary.setCuentaId(accountId);
            summary.setReportYear(reportYear);
            summary.setTransactionCount(transactions);
            summary.setAnomalyCount(anomalies);
            summary.setTotalDeposits(deposits);
            summary.setTotalWithdrawals(withdrawals);
            summary.setNetBalance(deposits.subtract(withdrawals));
            return summary;
        }
    }
}
