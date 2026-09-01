package com.bank.batch.mapper;

import com.bank.batch.model.Transaction;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

public class TransactionFieldSetMapper implements FieldSetMapper<Transaction> {
    private final FlexibleLocalDateParser dateParser;
    private final String sourceFile;

    public TransactionFieldSetMapper(FlexibleLocalDateParser dateParser, String sourceFile) {
        this.dateParser = dateParser;
        this.sourceFile = sourceFile;
    }

    @Override
    public Transaction mapFieldSet(FieldSet fieldSet) throws BindException {
        Transaction transaction = new Transaction();
        transaction.setId(FieldSetValues.requiredLong(fieldSet, "id"));
        transaction.setFecha(dateParser.parse(FieldSetValues.requiredText(fieldSet, "fecha")));
        transaction.setMonto(FieldSetValues.optionalDecimal(fieldSet, "monto"));
        transaction.setTipo(FieldSetValues.optionalText(fieldSet, "tipo"));
        transaction.setSourceFile(sourceFile);
        return transaction;
    }
}

