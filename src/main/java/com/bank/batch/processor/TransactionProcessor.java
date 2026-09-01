package com.bank.batch.processor;

import com.bank.batch.model.Transaction;
import com.bank.batch.exception.ValidationException;
import com.bank.batch.service.DuplicateTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class TransactionProcessor implements ItemProcessor<Transaction, Transaction> {
    private static final Logger logger = LoggerFactory.getLogger(TransactionProcessor.class);
    private final DuplicateTracker duplicateTracker;

    public TransactionProcessor(DuplicateTracker duplicateTracker) {
        this.duplicateTracker = duplicateTracker;
    }

    @Override
    public Transaction process(Transaction item) {
        if (item.getId() == null || item.getFecha() == null) {
            throw new ValidationException("La transacción no contiene ID o fecha");
        }
        List<String> errors = new ArrayList<>();
        if (item.getMonto() == null) {
            errors.add("MONTO_VACIO");
            item.setMonto(BigDecimal.ZERO);
        } else if (item.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("MONTO_NO_POSITIVO");
        }

        String type = item.getTipo() == null ? "" : item.getTipo().trim().toLowerCase(Locale.ROOT);
        item.setTipo(type.isBlank() ? "desconocido" : type);
        if (!type.equals("debito") && !type.equals("credito")) {
            errors.add("TIPO_TRANSACCION_INVALIDO");
        }

        item.setIsAnomaly(!errors.isEmpty());
        item.setErrorReason(errors.isEmpty() ? null : String.join(";", errors));

        String businessKey = item.getId() + "|" + item.getFecha() + "|"
                + item.getMonto().stripTrailingZeros().toPlainString() + "|" + item.getTipo();
        String recordKey = UUID.nameUUIDFromBytes(businessKey.getBytes(StandardCharsets.UTF_8)).toString();
        item.setRecordKey(recordKey);
        if (!duplicateTracker.registerTransaction(recordKey)) {
            logger.warn("REGISTRO_FILTRADO motivo=TRANSACCION_DUPLICADA key={} legacyId={} archivo={}",
                    recordKey, item.getId(), item.getSourceFile());
            return null;
        }
        return item;
    }
}
