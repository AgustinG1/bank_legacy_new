package com.bank.bff.api;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> business(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "Solicitud rechazada" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid(Exception ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Solicitud invalida; revise identificador y monto"));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<?> upstream(RestClientResponseException ex) {
        if (ex.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
        return ResponseEntity.status(502)
                .body(Map.of("error", "Servicio bancario no disponible"));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<?> connection(RestClientException ex) {
        return ResponseEntity.status(503)
                .body(Map.of("error", "No se pudo contactar al servicio bancario"));
    }
}
