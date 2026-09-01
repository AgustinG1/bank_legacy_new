package com.bank.batch.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "processed_account")
@Data
public class Account {
    @Id
    @Column(length = 36)
    private String recordKey;
    private Long cuentaId;
    private String nombre;
    @Column(precision = 19, scale = 2)
    private BigDecimal saldo;
    private Integer edad;
    private String tipo;
    @Column(precision = 7, scale = 4)
    private BigDecimal tasaAplicada;
    @Column(precision = 19, scale = 2)
    private BigDecimal interesCalculado;
    @Column(precision = 19, scale = 2)
    private BigDecimal saldoFinal;
    private String sourceFile;
}
