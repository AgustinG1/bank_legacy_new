package com.bank.batch.mapper;

import com.bank.batch.model.Account;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

public class AccountFieldSetMapper implements FieldSetMapper<Account> {
    private final String sourceFile;

    public AccountFieldSetMapper(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    @Override
    public Account mapFieldSet(FieldSet fieldSet) throws BindException {
        Account account = new Account();
        account.setCuentaId(FieldSetValues.requiredLong(fieldSet, "cuentaId"));
        account.setNombre(FieldSetValues.optionalText(fieldSet, "nombre"));
        account.setSaldo(FieldSetValues.optionalDecimal(fieldSet, "saldo"));
        account.setEdad(FieldSetValues.optionalInteger(fieldSet, "edad"));
        account.setTipo(FieldSetValues.optionalText(fieldSet, "tipo"));
        account.setSourceFile(sourceFile);
        return account;
    }
}

