package com.bank.batch.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DuplicateTracker {
    private final Set<String> transactionKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> accountKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> annualRecordKeys = ConcurrentHashMap.newKeySet();

    public boolean registerTransaction(String key) {
        return transactionKeys.add(key);
    }

    public boolean registerAccount(String key) {
        return accountKeys.add(key);
    }

    public boolean registerAnnualRecord(String key) {
        return annualRecordKeys.add(key);
    }

    public void reset() {
        transactionKeys.clear();
        accountKeys.clear();
        annualRecordKeys.clear();
    }
}
