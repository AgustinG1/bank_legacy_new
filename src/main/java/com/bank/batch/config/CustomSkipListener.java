package com.bank.batch.config;

import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class CustomSkipListener implements SkipListener<Object, Object> {

    private static final Logger logger = LoggerFactory.getLogger(CustomSkipListener.class);

    @Override
    public void onSkipInRead(Throwable t) {
        logger.warn("REGISTRO_RECHAZADO fase=LECTURA excepcion={} mensaje={}",
                t.getClass().getSimpleName(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        logger.warn("REGISTRO_RECHAZADO fase=ESCRITURA item={} excepcion={} mensaje={}",
                item, t.getClass().getSimpleName(), t.getMessage());
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        logger.warn("REGISTRO_RECHAZADO fase=PROCESAMIENTO item={} excepcion={} mensaje={}",
                item, t.getClass().getSimpleName(), t.getMessage());
    }
}
