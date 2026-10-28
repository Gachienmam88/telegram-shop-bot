package com.shopbot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopbot.domain.BalanceLog;
import com.shopbot.domain.BalanceReason;

public interface BalanceLogRepository extends JpaRepository<BalanceLog, Long> {

    List<BalanceLog> findTop15ByUserIdOrderByIdDesc(Long userId);

    @Query("select coalesce(sum(b.delta), 0) from BalanceLog b where b.userId = :userId and b.reason = :reason")
    long sumDelta(@Param("userId") Long userId, @Param("reason") BalanceReason reason);
}
