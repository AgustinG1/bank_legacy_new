package com.bank.bff.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public final class Models {
    private Models() {}

    public record Cuenta(String clave, Long cuentaId, String nombre, Integer edad, String tipo,
                         BigDecimal saldoInicial, BigDecimal tasaAplicada, BigDecimal interesCalculado,
                         BigDecimal saldoFinalBatch, BigDecimal saldoDisponible) {}
    public record Resumen(Long cuentaId, String tipo, BigDecimal saldoDisponible) {}
    public record Saldo(Long cuentaId, BigDecimal saldoDisponible) {}
    public record SolicitudRetiro(
            @NotNull UUID solicitudId,
            @NotNull @DecimalMin("0.01") @DecimalMax("200000.00")
            @Digits(integer = 6, fraction = 2) BigDecimal monto) {}
    public record Retiro(UUID solicitudId, BigDecimal monto, BigDecimal saldoRestante) {}

    public record ComprobanteRetiro(UUID solicitudId, BigDecimal monto,
                                    BigDecimal saldoDisponible, String estado) {}

    public record Movimiento(String clave, Long cuentaId, LocalDate fecha, String tipo,
                             BigDecimal monto, String descripcion, boolean anomalia) {}

    public record MovimientoBreve(LocalDate fecha, String tipo, BigDecimal monto) {}

    public record PaginaMovimientos(List<Movimiento> contenido, int pagina, int tamanio,
                                    long totalElementos) {}

    public record ResumenAnual(Long cuentaId, Integer anio, long cantidadMovimientos,
                              long cantidadAnomalias, BigDecimal totalDepositos,
                              BigDecimal totalRetiros, BigDecimal saldoNeto) {}
}
