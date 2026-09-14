package com.bank.bff.atm;

import com.bank.bff.api.Models.ComprobanteRetiro;
import com.bank.bff.api.Models.Retiro;
import com.bank.bff.api.Models.Saldo;
import com.bank.bff.api.Models.SolicitudRetiro;
import com.bank.bff.client.CoreClient;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cajero")
public class AtmController {
    private final CoreClient core;

    public AtmController(CoreClient core) {
        this.core = core;
    }

    @GetMapping("/saldo")
    public Saldo saldo(@RequestHeader("Authorization") String token) {
        return core.saldo(token);
    }

    @PostMapping("/retiros")
    public ComprobanteRetiro retirar(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody SolicitudRetiro request) {
        Retiro retiro = core.retirar(token, request);
        return new ComprobanteRetiro(
                retiro.solicitudId(),
                retiro.monto(),
                retiro.saldoRestante(),
                "APROBADO");
    }
}
