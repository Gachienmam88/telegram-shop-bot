package com.shopbot.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopbot.domain.Topup;
import com.shopbot.domain.TopupStatus;

import jakarta.persistence.LockModeType;

public interface TopupRepository extends JpaRepository<Topup, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Topup t where t.code = :code")
    Optional<Topup> findForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Topup t where t.payRef = :payRef")
    Optional<Topup> findByPayRefForUpdate(@Param("payRef") String payRef);

    Optional<Topup> findByPayRef(String payRef);

    @Query("select t.code from Topup t where t.status = :status and t.expiresAt < :now")
    List<String> findCodesByStatusAndExpiresAtBefore(@Param("status") TopupStatus status, @Param("now") Instant now);
}
