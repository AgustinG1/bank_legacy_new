package com.bank.batch.processor;

import com.bank.batch.model.AnnualStatement;
import com.bank.batch.exception.ValidationException;
import com.bank.batch.service.DuplicateTracker;
import org.springframework.batch.item.ItemProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class AnnualStatementProcessor implements ItemProcessor<AnnualStatement, AnnualStatement> {
    private static final Logger logger = LoggerFactory.getLogger(AnnualStatementProcessor.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("deposito", "retiro", "compra", "pago");
    private final DuplicateTracker duplicateTracker;

    public AnnualStatementProcessor(DuplicateTracker duplicateTracker) {
        this.duplicateTracker = duplicateTracker;
    }

    @Override
    public AnnualStatement process(AnnualStatement item) {
        if (item.getCuentaId() == null || item.getFecha() == null) {
            throw new ValidationException("El movimiento anual no contiene cuenta o fecha");
        }

        List<String> errors = new ArrayList<>();
        String type = normalize(item.getTransaccion());
        if (!ALLOWED_TYPES.contains(type)) {
            logger.warn("REGISTRO_FILTRADO motivo=TIPO_MOVIMIENTO_INVALIDO cuentaId={} tipo={} archivo={}",
                    item.getCuentaId(), item.getTransaccion(), item.getSourceFile());
            return null;
        }

        if (item.getMonto() == null) {
            item.setMonto(BigDecimal.ZERO);
            errors.add("MONTO_VACIO");
        }

        if (type.equals("deposito") && item.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("DEPOSITO_NO_POSITIVO");
        }
        if ((type.equals("retiro") || type.equals("compra") || type.equals("pago"))
                && item.getMonto().compareTo(BigDecimal.ZERO) > 0) {
            errors.add("EGRESO_POSITIVO");
        }

        String description = item.getDescripcion();
        if (description == null || description.isBlank()) {
            description = "SIN DESCRIPCION";
            errors.add("DESCRIPCION_VACIA");
        } else {
            description = description.trim().toUpperCase(Locale.ROOT);
        }

        item.setTransaccion(type);
        item.setDescripcion(description);
        item.setReportYear(item.getFecha().getYear());
        item.setIsAnomaly(!errors.isEmpty());
        item.setErrorReason(errors.isEmpty() ? null : String.join(";", errors));

        String businessKey = item.getCuentaId() + "|" + item.getFecha() + "|" + type + "|"
                + item.getMonto().stripTrailingZeros().toPlainString() + "|" + description;
        String recordKey = UUID.nameUUIDFromBytes(businessKey.getBytes(StandardCharsets.UTF_8)).toString();
        item.setRecordKey(recordKey);
        if (!duplicateTracker.registerAnnualRecord(recordKey)) {
            logger.warn("REGISTRO_FILTRADO motivo=MOVIMIENTO_ANUAL_DUPLICADO key={} archivo={}",
                    recordKey, item.getSourceFile());
            return null;
        }

        return item;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
