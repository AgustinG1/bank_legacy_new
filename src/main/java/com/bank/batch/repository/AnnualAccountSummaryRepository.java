package com.bank.batch.repository;

import com.bank.batch.model.AnnualAccountSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnualAccountSummaryRepository extends JpaRepository<AnnualAccountSummary, String> {
}

