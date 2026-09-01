package com.bank.batch.mapper;

import com.bank.batch.model.AnnualStatement;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

public class AnnualStatementFieldSetMapper implements FieldSetMapper<AnnualStatement> {
    private final FlexibleLocalDateParser dateParser;
    private final String sourceFile;

    public AnnualStatementFieldSetMapper(FlexibleLocalDateParser dateParser, String sourceFile) {
        this.dateParser = dateParser;
        this.sourceFile = sourceFile;
    }

    @Override
    public AnnualStatement mapFieldSet(FieldSet fieldSet) throws BindException {
        AnnualStatement statement = new AnnualStatement();
        statement.setCuentaId(FieldSetValues.requiredLong(fieldSet, "cuentaId"));
        statement.setFecha(dateParser.parse(FieldSetValues.requiredText(fieldSet, "fecha")));
        statement.setTransaccion(FieldSetValues.optionalText(fieldSet, "transaccion"));
        statement.setMonto(FieldSetValues.optionalDecimal(fieldSet, "monto"));
        statement.setDescripcion(FieldSetValues.optionalText(fieldSet, "descripcion"));
        statement.setSourceFile(sourceFile);
        return statement;
    }
}
