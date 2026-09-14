package com.bank.bff.mobile;

import com.bank.bff.api.Models.MovimientoBreve;
import com.bank.bff.api.Models.Resumen;
import com.bank.bff.client.CoreClient;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movil")
public class MobileController {
    private final CoreClient core;
    private final MobileReportsClient reports;

    public MobileController(CoreClient core, MobileReportsClient reports) {
        this.core = core;
        this.reports = reports;
    }

    public record ResumenMovil(Long cuentaId, String tipo, BigDecimal saldoDisponible,
                               List<MovimientoBreve> ultimosMovimientos) {}

    @GetMapping("/resumen")
    public ResumenMovil resumen(@RequestHeader("Authorization") String token) {
        Resumen cuenta = core.resumen(token);
        List<MovimientoBreve> movimientos = Arrays.asList(
                reports.ultimos(token, cuenta.cuentaId()));
        return new ResumenMovil(
                cuenta.cuentaId(), cuenta.tipo(), cuenta.saldoDisponible(), movimientos);
    }
}
