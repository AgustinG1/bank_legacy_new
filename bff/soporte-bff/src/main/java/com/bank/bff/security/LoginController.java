package com.bank.bff.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
public class LoginController {
    public record Login(@NotBlank @Size(max=64) String usuario,
                        @NotBlank @Size(max=72) String clave) {}
    public record AtmLogin(@NotBlank @Size(max=36) String cuenta,
                           @NotBlank @Pattern(regexp="[0-9]{4,6}") String pin) {}
    private final ChannelLogin login;
    public LoginController(ChannelLogin login) { this.login = login; }

    @PostMapping("/api/web/login")
    public ChannelLogin.Token web(@Valid @RequestBody Login request) {
        return login.login("WEB", request.usuario(), request.clave());
    }

    @PostMapping("/api/movil/login")
    public ChannelLogin.Token mobile(@Valid @RequestBody Login request) {
        return login.login("MOBILE", request.usuario(), request.clave());
    }

    @PostMapping("/api/cajero/sesion")
    public ChannelLogin.Token atm(@Valid @RequestBody AtmLogin request,
            @RequestHeader(value="X-ATM-Terminal", required=false) String terminal,
            @RequestHeader(value="X-ATM-Key", required=false) String key) {
        return login.atm(request.cuenta(), request.pin(), terminal, key);
    }
}
