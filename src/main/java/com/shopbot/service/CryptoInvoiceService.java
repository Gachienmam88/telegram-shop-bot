package com.shopbot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.CryptoInvoice;
import com.shopbot.domain.CryptoNetwork;
import com.shopbot.repository.CryptoInvoiceRepository;

/**
 * Tạo invoice USDT với số tiền tròn (tối đa 2 chữ số thập phân): khách chuyển sao cho ví shop nhận ĐÚNG số này
 * (phí mạng khách tự trả). Nhìn số tiền nhận được là biết của đơn nào → trên 1 mạng, 1 số tiền chỉ được giữ
 * bởi 1 invoice chưa thanh toán; khách thứ 2 trùng số tiền phải chọn mạng khác hoặc đợi.
 * Quy đổi USDT ↔ VNĐ theo tỷ giá cố định USDT_RATE.
 */
@Service
public class CryptoInvoiceService {

    /** Mã lỗi khi số tiền đang bị invoice khác giữ trên cùng mạng. */
    public static final String AMOUNT_TAKEN = "crypto.amount_taken";
    /** Giữ số tiền thêm 10 phút sau hạn thanh toán để giao dịch gửi sát giờ vẫn khớp đúng người. */
    static final Duration MATCH_GRACE = Duration.ofMinutes(10);
    /** Khóa advisory của Postgres: 2 invoice tạo cùng lúc không giữ trùng số tiền. */
    private static final long LOCK_KEY = 0x55534454L;

    private final CryptoInvoiceRepository invoices;
    private final JdbcTemplate jdbc;
    private final ShopProperties props;
    private final Clock clock;

    public CryptoInvoiceService(CryptoInvoiceRepository invoices, JdbcTemplate jdbc, ShopProperties props,
                                Clock clock) {
        this.invoices = invoices;
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    /** Phải gọi trong transaction của đơn/nạp để invoice và đơn cùng commit hoặc cùng rollback. */
    @Transactional
    public CryptoInvoice create(CryptoNetwork network, BigDecimal usdt, long vndAmount, String payRef,
                                Instant expiresAt) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (ResultSetExtractor<Void>) rs -> null, LOCK_KEY);
        Instant now = clock.instant();
        BigDecimal amount = usdt.setScale(2, RoundingMode.CEILING);
        if (invoices.existsByNetworkAndUsdtAmountAndMatchedAtIsNullAndMatchUntilAfter(network, amount, now)) {
            throw new ShopException(AMOUNT_TAKEN, amount.stripTrailingZeros().toPlainString(), network.displayName());
        }
        CryptoInvoice i = new CryptoInvoice();
        i.setNetwork(network);
        i.setUsdtAmount(amount);
        i.setPayRef(payRef);
        i.setVndAmount(vndAmount);
        i.setCreatedAt(now);
        i.setExpiresAt(expiresAt);
        i.setMatchUntil(expiresAt.plus(MATCH_GRACE));
        return invoices.save(i);
    }

    /** Giá VNĐ → USDT, làm tròn LÊN 2 chữ số (shop không bị thiệt). VD 120.000 / 27.000 = 4.444 → 4.45. */
    public BigDecimal toUsdt(long vnd) {
        return BigDecimal.valueOf(vnd).divide(rate(), 2, RoundingMode.CEILING);
    }

    /** USDT → VNĐ, làm tròn XUỐNG. */
    public long toVnd(BigDecimal usdt) {
        return usdt.multiply(rate()).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    public BigDecimal rate() {
        BigDecimal r = props.payment().usdtRate();
        if (r == null || r.signum() <= 0) {
            throw new IllegalStateException("USDT_RATE must be > 0");
        }
        return r;
    }
}
