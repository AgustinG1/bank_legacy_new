package com.bank.batch.config;

import com.bank.batch.exception.ValidationException;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class CustomSkipPolicy implements SkipPolicy {

    private static final Logger logger = LoggerFactory.getLogger(CustomSkipPolicy.class);
    private final long skipLimit;

    public CustomSkipPolicy(@Value("${bank.batch.skip-limit:2000}") long skipLimit) {
        this.skipLimit = skipLimit;
    }

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        if (skipCount >= skipLimit) {
            return false;
        }

        boolean skippable = hasCause(t, ValidationException.class)
                || hasCause(t, FlatFileParseException.class)
                || hasCause(t, DataIntegrityViolationException.class);
        if (skippable) {
            logger.warn("SKIP_APROBADO numero={} limite={} excepcion={} mensaje={}",
                    skipCount + 1, skipLimit, t.getClass().getSimpleName(), t.getMessage());
        } else {
            logger.error("SKIP_RECHAZADO excepcion_no_clasificada={} mensaje={}",
                    t.getClass().getName(), t.getMessage());
        }
        return skippable;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> expected) {
        Throwable current = throwable;
        while (current != null) {
            if (expected.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
