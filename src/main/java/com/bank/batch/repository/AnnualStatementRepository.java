package com.bank.batch.repository;

import com.bank.batch.model.AnnualStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnualStatementRepository extends JpaRepository<AnnualStatement, String> {}
