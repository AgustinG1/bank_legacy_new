package com.bank.bff.client;

import com.bank.bff.api.Models.Cuenta;
import com.bank.bff.api.Models.Retiro;
import com.bank.bff.api.Models.Resumen;
import com.bank.bff.api.Models.Saldo;
import com.bank.bff.api.Models.SolicitudRetiro;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CoreClient {
    private final RestClient client;

    public CoreClient(@Value("${bank.core-url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public Cuenta detalle(String authorization) {
        return client.get().uri("/interno/cuentas/detalle")
                .header("Authorization", authorization)
                .retrieve().body(Cuenta.class);
    }

    public Resumen resumen(String authorization) {
        return client.get().uri("/interno/cuentas/resumen")
                .header("Authorization", authorization)
                .retrieve().body(Resumen.class);
    }

    public Saldo saldo(String authorization) {
        return client.get().uri("/interno/cuentas/saldo")
                .header("Authorization", authorization)
                .retrieve().body(Saldo.class);
    }

    public Retiro[] retiros(String authorization) {
        return client.get().uri("/interno/retiros")
                .header("Authorization", authorization)
                .retrieve().body(Retiro[].class);
    }

    public Retiro retirar(String authorization, SolicitudRetiro request) {
        return client.post().uri("/interno/retiros")
                .header("Authorization", authorization)
                .body(request).retrieve().body(Retiro.class);
    }
}
