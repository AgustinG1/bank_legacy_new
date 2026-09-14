package com.bank.bff.web;

import com.bank.bff.api.Models.Cuenta;
import com.bank.bff.api.Models.PaginaMovimientos;
import com.bank.bff.api.Models.Retiro;
import com.bank.bff.api.Models.ResumenAnual;
import com.bank.bff.client.CoreClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web")
public class WebController {
    private final CoreClient core;
    private final ReportsClient reports;
    private final Executor aggregationExecutor;

    public WebController(
            CoreClient core,
            ReportsClient reports,
            @Qualifier("webAggregationExecutor") Executor aggregationExecutor) {
        this.core = core;
        this.reports = reports;
        this.aggregationExecutor = aggregationExecutor;
    }

    public record Panel(Cuenta cuenta, Retiro[] retirosRecientes,
                        PaginaMovimientos movimientos, ResumenAnual[] resumenesAnuales) {}

    @GetMapping("/panel")
    public Panel panel(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio) {
        Cuenta cuenta = core.detalle(token);
        var retiros = CompletableFuture.supplyAsync(
                () -> core.retiros(token), aggregationExecutor);
        var movimientos = CompletableFuture.supplyAsync(
                () -> reports.movimientos(token, cuenta.cuentaId(), pagina, tamanio),
                aggregationExecutor);
        var resumenes = CompletableFuture.supplyAsync(
                () -> reports.resumenes(token, cuenta.cuentaId()), aggregationExecutor);
        return new Panel(
                cuenta,
                join(retiros),
                join(movimientos),
                join(resumenes));
    }

    private <T> T join(CompletableFuture<T> operation) {
        try {
            return operation.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw exception;
        }
    }
}
