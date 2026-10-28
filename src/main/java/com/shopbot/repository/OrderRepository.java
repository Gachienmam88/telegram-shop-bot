package com.shopbot.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopbot.domain.OrderStatus;
import com.shopbot.domain.ShopOrder;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<ShopOrder, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ShopOrder o where o.code = :code")
    Optional<ShopOrder> findForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ShopOrder o where o.payRef = :payRef")
    Optional<ShopOrder> findByPayRefForUpdate(@Param("payRef") String payRef);

    Optional<ShopOrder> findByPayRef(String payRef);

    @Query("select o.code from ShopOrder o where o.status = :status and o.expiresAt < :now")
    List<String> findCodesByStatusAndExpiresAtBefore(@Param("status") OrderStatus status, @Param("now") Instant now);

    List<ShopOrder> findTop10ByUserIdAndStatusOrderByPaidAtDesc(Long userId, OrderStatus status);

    @Query("select coalesce(sum(o.amount), 0) from ShopOrder o where o.status = :status and o.paidAt >= :from")
    long sumAmountPaidSince(@Param("status") OrderStatus status, @Param("from") Instant from);

    @Query("select count(o) from ShopOrder o where o.status = :status and o.paidAt >= :from")
    long countPaidSince(@Param("status") OrderStatus status, @Param("from") Instant from);
}
