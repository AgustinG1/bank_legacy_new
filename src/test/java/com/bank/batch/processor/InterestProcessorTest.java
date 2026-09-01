package com.bank.batch.processor;

import com.bank.batch.model.Account;
import com.bank.batch.service.DuplicateTracker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InterestProcessorTest {

    @Test
    void appliesFivePercentToSavingsAndLoans() {
        InterestProcessor processor = new InterestProcessor(new DuplicateTracker());

        Account savings = processor.process(account(1L, "ahorro", new BigDecimal("1000"), 30));
        Account loan = processor.process(account(2L, "prestamo", new BigDecimal("2000"), 40));

        assertThat(savings.getInteresCalculado()).isEqualByComparingTo("50.00");
        assertThat(savings.getSaldoFinal()).isEqualByComparingTo("1050.00");
        assertThat(loan.getInteresCalculado()).isEqualByComparingTo("100.00");
        assertThat(loan.getSaldoFinal()).isEqualByComparingTo("2100.00");
    }

    @Test
    void rejectsUnknownAccountType() {
        InterestProcessor processor = new InterestProcessor(new DuplicateTracker());

        assertThat(processor.process(account(1L, "hipoteca", new BigDecimal("1000"), 30))).isNull();
    }

    private Account account(Long id, String type, BigDecimal balance, int age) {
        Account account = new Account();
        account.setCuentaId(id);
        account.setNombre("Cliente " + id);
        account.setTipo(type);
        account.setSaldo(balance);
        account.setEdad(age);
        return account;
    }
}
