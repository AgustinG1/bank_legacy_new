package com.bank.batch.config;

import com.bank.batch.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomSkipPolicyTest {

    @Test
    void skipsOnlyClassifiedDataErrorsWithinLimit() {
        CustomSkipPolicy policy = new CustomSkipPolicy(2);

        assertThat(policy.shouldSkip(new ValidationException("dato inválido"), 0)).isTrue();
        assertThat(policy.shouldSkip(new IllegalStateException("error de programación"), 0)).isFalse();
        assertThat(policy.shouldSkip(new ValidationException("sobre límite"), 2)).isFalse();
    }
}
