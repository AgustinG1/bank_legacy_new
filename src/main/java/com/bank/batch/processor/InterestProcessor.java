package com.bank.batch.processor;

import com.bank.batch.model.Account;
import com.bank.batch.service.DuplicateTracker;
import org.springframework.batch.item.ItemProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Component
public class InterestProcessor implements ItemProcessor<Account, Account> {
    private static final Logger logger = LoggerFactory.getLogger(InterestProcessor.class);
    private static final BigDecimal INTEREST_RATE = new BigDecimal("0.05");
    private final DuplicateTracker duplicateTracker;

    public InterestProcessor(DuplicateTracker duplicateTracker) {
        this.duplicateTracker = duplicateTracker;
    }

    @Override
    public Account process(Account item) {
        if (item.getCuentaId() == null) {
            return reject(item, "CUENTA_SIN_IDENTIFICADOR");
        }
        if (item.getNombre() == null || item.getNombre().isBlank()) {
            return reject(item, "NOMBRE_VACIO");
        }
        if (item.getSaldo() == null || item.getSaldo().compareTo(BigDecimal.ZERO) < 0) {
            return reject(item, "SALDO_VACIO_O_NEGATIVO");
        }
        if (item.getEdad() == null || item.getEdad() < 18 || item.getEdad() > 100) {
            return reject(item, "EDAD_FUERA_DE_RANGO");
        }
        String type = item.getTipo() == null ? "" : item.getTipo().trim().toLowerCase(Locale.ROOT);
        if (!type.equals("ahorro") && !type.equals("prestamo")) {
            return reject(item, "TIPO_CUENTA_INVALIDO:" + type);
        }
        BigDecimal balance = item.getSaldo().setScale(2, RoundingMode.HALF_UP);
        BigDecimal interest = balance.multiply(INTEREST_RATE).setScale(2, RoundingMode.HALF_UP);
        item.setNombre(item.getNombre().trim());
        item.setTipo(type);
        item.setSaldo(balance);
        item.setTasaAplicada(INTEREST_RATE);
        item.setInteresCalculado(interest);
        item.setSaldoFinal(balance.add(interest).setScale(2, RoundingMode.HALF_UP));

        String businessKey = item.getCuentaId() + "|" + item.getNombre().toLowerCase(Locale.ROOT) + "|"
                + balance.toPlainString() + "|" + item.getEdad() + "|" + type;
        String recordKey = UUID.nameUUIDFromBytes(businessKey.getBytes(StandardCharsets.UTF_8)).toString();
        item.setRecordKey(recordKey);
        if (!duplicateTracker.registerAccount(recordKey)) {
            return reject(item, "CUENTA_DUPLICADA");
        }
        return item;
    }

    private Account reject(Account item, String reason) {
        logger.warn("REGISTRO_FILTRADO motivo={} cuentaId={} archivo={}", reason,
                item.getCuentaId(), item.getSourceFile());
        return null;
    }
}
