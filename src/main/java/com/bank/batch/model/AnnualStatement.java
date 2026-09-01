package com.bank.batch.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "annual_statement")
@Data
public class AnnualStatement {
    @Id
    @Column(length = 36)
    private String recordKey;
    private Long cuentaId;
    private LocalDate fecha;
    private String transaccion;
    @Column(precision = 19, scale = 2)
    private BigDecimal monto;
    private String descripcion;
    private Integer reportYear;
    private Boolean isAnomaly;
    @Column(length = 500)
    private String errorReason;
    private String sourceFile;
}
