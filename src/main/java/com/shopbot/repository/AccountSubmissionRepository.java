package com.shopbot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopbot.domain.AccountSubmission;

public interface AccountSubmissionRepository extends JpaRepository<AccountSubmission, Long> {

    Optional<AccountSubmission> findByCode(String code);
}
