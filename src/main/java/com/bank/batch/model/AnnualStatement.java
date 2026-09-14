package com.bank.batch.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "annual_statement", indexes = {
        @Index(name = "idx_annual_statement_account_date",
                columnList = "cuenta_id, fecha, record_key")
})
@Data
public class AnnualStatement {
    @Id
    @Column(name = "record_key", length = 36)
    private String recordKey;
    @Column(name = "cuenta_id")
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
