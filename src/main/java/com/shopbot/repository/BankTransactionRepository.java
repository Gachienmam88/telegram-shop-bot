package com.shopbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopbot.domain.BankTransaction;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
}
