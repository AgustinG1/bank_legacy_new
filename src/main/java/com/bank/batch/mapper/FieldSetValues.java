package com.bank.batch.mapper;

import com.bank.batch.exception.ValidationException;
import org.springframework.batch.item.file.transform.FieldSet;

import java.math.BigDecimal;

final class FieldSetValues {
    private FieldSetValues() {
    }

    static String optionalText(FieldSet fieldSet, String name) {
        String value = fieldSet.readString(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String requiredText(FieldSet fieldSet, String name) {
        String value = optionalText(fieldSet, name);
        if (value == null) {
            throw new ValidationException("El campo " + name + " es obligatorio");
        }
        return value;
    }

    static Long requiredLong(FieldSet fieldSet, String name) {
        String value = requiredText(fieldSet, name);
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ValidationException("El campo " + name + " no es un entero válido: " + value, exception);
        }
    }

    static Integer optionalInteger(FieldSet fieldSet, String name) {
        String value = optionalText(fieldSet, name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ValidationException("El campo " + name + " no es un entero válido: " + value, exception);
        }
    }

    static BigDecimal optionalDecimal(FieldSet fieldSet, String name) {
        String value = optionalText(fieldSet, name);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new ValidationException("El campo " + name + " no es un número válido: " + value, exception);
        }
    }
}

