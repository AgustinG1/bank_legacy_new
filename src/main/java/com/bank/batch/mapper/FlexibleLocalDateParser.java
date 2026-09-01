package com.bank.batch.mapper;

import com.bank.batch.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

@Component
public class FlexibleLocalDateParser {
    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            strict("uuuu-MM-dd"),
            strict("uuuu/MM/dd"),
            strict("dd-MM-uuuu"),
            strict("dd/MM/uuuu")
    );

    public LocalDate parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ValidationException("La fecha es obligatoria");
        }

        String value = rawValue.trim();
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Se prueba el siguiente formato permitido.
            }
        }
        throw new ValidationException("Formato de fecha no soportado: " + value);
    }

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }
}

