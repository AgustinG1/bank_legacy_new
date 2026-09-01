package com.bank.batch.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "processed_transaction")
@Data
public class Transaction {
    @Id
    @Column(length = 36)
    private String recordKey;
    private Long id;
    private LocalDate fecha;
    @Column(precision = 19, scale = 2)
    private BigDecimal monto;
    private String tipo;
    private Boolean isAnomaly;
    @Column(length = 500)
    private String errorReason;
    private String sourceFile;
}
