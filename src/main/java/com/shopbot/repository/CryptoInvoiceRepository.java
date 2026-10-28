package com.shopbot.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopbot.domain.CryptoInvoice;
import com.shopbot.domain.CryptoNetwork;

public interface CryptoInvoiceRepository extends JpaRepository<CryptoInvoice, Long> {

    /** Số tiền đang bị invoice khác (chưa nhận tiền, còn trong thời gian giữ) chiếm trên mạng này. */
    boolean existsByNetworkAndUsdtAmountAndMatchedAtIsNullAndMatchUntilAfter(CryptoNetwork network,
                                                                            BigDecimal usdtAmount, Instant now);

    /** Invoice khớp đúng số tiền: invoice chưa nhận tiền trước, rồi mới nhất trước. */
    @Query("select i from CryptoInvoice i where i.network = :network and i.usdtAmount = :amount"
            + " and i.matchUntil > :now order by case when i.matchedAt is null then 0 else 1 end, i.createdAt desc")
    List<CryptoInvoice> findMatching(@Param("network") CryptoNetwork network, @Param("amount") BigDecimal amount,
                                     @Param("now") Instant now);

    /** Invoice còn hạn thanh toán và chưa nhận tiền — dùng khi khách chuyển lệch số tiền. */
    @Query("select i from CryptoInvoice i where i.network = :network and i.matchedAt is null and i.expiresAt > :now")
    List<CryptoInvoice> findOpen(@Param("network") CryptoNetwork network, @Param("now") Instant now);

    Optional<CryptoInvoice> findFirstByPayRefOrderByIdDesc(String payRef);
}
