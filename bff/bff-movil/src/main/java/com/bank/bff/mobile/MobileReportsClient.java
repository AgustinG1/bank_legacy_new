package com.bank.bff.mobile;

import com.bank.bff.api.Models.MovimientoBreve;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MobileReportsClient {
    private final RestClient client;

    public MobileReportsClient(@Value("${bank.reports-url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public MovimientoBreve[] ultimos(String authorization, Long cuentaId) {
        return client.get()
                .uri(builder -> builder
                        .path("/interno/reportes/cuentas/{cuentaId}/movimientos-breves")
                        .queryParam("limite", 3)
                        .build(cuentaId))
                .header("Authorization", authorization)
                .retrieve()
                .body(MovimientoBreve[].class);
    }
}
