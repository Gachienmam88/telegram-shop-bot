package com.shopbot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopbot.domain.Withdrawal;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {

    Optional<Withdrawal> findByCode(String code);
}
