package com.bank.batch.repository;

import com.bank.batch.model.DailyTransactionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DailyTransactionSummaryRepository extends JpaRepository<DailyTransactionSummary, LocalDate> {
}

