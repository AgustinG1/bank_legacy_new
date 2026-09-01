package com.bank.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
public class CustomRetryListener implements RetryListener {
    private static final Logger logger = LoggerFactory.getLogger(CustomRetryListener.class);

    @Override
    public <T, E extends Throwable> void onError(RetryContext context,
                                                 RetryCallback<T, E> callback,
                                                 Throwable throwable) {
        if (throwable instanceof TransientDataAccessException) {
            logger.warn("RETRY intento={} excepcion={} mensaje={}", context.getRetryCount(),
                    throwable.getClass().getSimpleName(), throwable.getMessage());
        }
    }
}
