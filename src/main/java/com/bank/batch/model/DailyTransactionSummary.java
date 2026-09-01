package com.bank.batch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_transaction_summary")
@Data
public class DailyTransactionSummary {
    @Id
    private LocalDate reportDate;
    private long totalTransactions;
    private long creditCount;
    private long debitCount;
    private long anomalyCount;
    @Column(precision = 19, scale = 2)
    private BigDecimal totalCredits;
    @Column(precision = 19, scale = 2)
    private BigDecimal totalDebits;
}

