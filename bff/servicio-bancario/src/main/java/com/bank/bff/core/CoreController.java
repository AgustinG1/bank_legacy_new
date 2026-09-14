package com.bank.bff.core;

import com.bank.bff.api.Models.Cuenta;
import com.bank.bff.api.Models.Retiro;
import com.bank.bff.api.Models.Resumen;
import com.bank.bff.api.Models.Saldo;
import com.bank.bff.api.Models.SolicitudRetiro;
import com.bank.bff.security.BankPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/interno")
public class CoreController {
    private final BankService bank;

    public CoreController(BankService bank) {
        this.bank = bank;
    }

    @GetMapping("/cuentas/detalle")
    public Cuenta detalle(@AuthenticationPrincipal BankPrincipal principal) {
        return bank.detalle(principal.accountKey());
    }

    @GetMapping("/cuentas/resumen")
    public Resumen resumen(@AuthenticationPrincipal BankPrincipal principal) {
        return bank.resumen(principal.accountKey());
    }

    @GetMapping("/cuentas/saldo")
    public Saldo saldo(@AuthenticationPrincipal BankPrincipal principal) {
        return bank.saldo(principal.accountKey());
    }

    @GetMapping("/retiros")
    public List<Retiro> retiros(@AuthenticationPrincipal BankPrincipal principal) {
        return bank.retiros(principal.accountKey());
    }

    @PostMapping("/retiros")
    public Retiro retirar(
            @AuthenticationPrincipal BankPrincipal principal,
            @Valid @RequestBody SolicitudRetiro request) {
        return bank.retirar(principal.accountKey(), request);
    }
}
