package com.shopbot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopbot.domain.UserAccount;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

    /** Khóa dòng user (SELECT ... FOR UPDATE) trước khi đổi số dư. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.userId = :id")
    Optional<UserAccount> findForUpdate(@Param("id") Long id);

    long countByReferrerId(Long referrerId);
}
