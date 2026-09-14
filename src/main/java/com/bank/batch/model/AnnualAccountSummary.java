package com.bank.batch.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "annual_account_summary", indexes = {
        @Index(name = "idx_annual_summary_account_year",
                columnList = "cuenta_id, report_year")
})
@Data
public class AnnualAccountSummary {
    @Id
    private String id;
    @Column(name = "cuenta_id")
    private Long cuentaId;
    @Column(name = "report_year")
    private Integer reportYear;
    private long transactionCount;
    private long anomalyCount;
    @Column(precision = 19, scale = 2)
    private BigDecimal totalDeposits;
    @Column(precision = 19, scale = 2)
    private BigDecimal totalWithdrawals;
    @Column(precision = 19, scale = 2)
    private BigDecimal netBalance;
}
