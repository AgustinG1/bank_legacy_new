package com.bank.bff.reports;

import com.bank.bff.api.Models.PaginaMovimientos;
import com.bank.bff.api.Models.MovimientoBreve;
import com.bank.bff.api.Models.ResumenAnual;
import com.bank.bff.security.BankPrincipal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/interno/reportes")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos")
    public PaginaMovimientos movimientos(
            @AuthenticationPrincipal BankPrincipal principal,
            @PathVariable Long cuentaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio) {
        return reports.movimientos(principal.accountKey(), cuentaId, pagina, tamanio);
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos-breves")
    public List<MovimientoBreve> movimientosBreves(
            @AuthenticationPrincipal BankPrincipal principal,
            @PathVariable Long cuentaId,
            @RequestParam(defaultValue = "3") int limite) {
        return reports.movimientosBreves(principal.accountKey(), cuentaId, limite);
    }

    @GetMapping("/cuentas/{cuentaId}/resumenes-anuales")
    public List<ResumenAnual> resumenes(
            @AuthenticationPrincipal BankPrincipal principal,
            @PathVariable Long cuentaId) {
        return reports.resumenes(principal.accountKey(), cuentaId);
    }
}
