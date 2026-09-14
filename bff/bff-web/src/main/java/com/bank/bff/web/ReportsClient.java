package com.bank.bff.web;

import com.bank.bff.api.Models.PaginaMovimientos;
import com.bank.bff.api.Models.ResumenAnual;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ReportsClient {
    private final RestClient client;

    public ReportsClient(@Value("${bank.reports-url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public PaginaMovimientos movimientos(
            String authorization, Long cuentaId, int pagina, int tamanio) {
        return client.get()
                .uri(builder -> builder
                        .path("/interno/reportes/cuentas/{cuentaId}/movimientos")
                        .queryParam("pagina", pagina)
                        .queryParam("tamanio", tamanio)
                        .build(cuentaId))
                .header("Authorization", authorization)
                .retrieve()
                .body(PaginaMovimientos.class);
    }

    public ResumenAnual[] resumenes(String authorization, Long cuentaId) {
        return client.get()
                .uri("/interno/reportes/cuentas/{cuentaId}/resumenes-anuales", cuentaId)
                .header("Authorization", authorization)
                .retrieve()
                .body(ResumenAnual[].class);
    }
}
