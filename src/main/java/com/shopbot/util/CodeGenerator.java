package com.shopbot.util;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.shopbot.config.ShopProperties;

/** Sinh mã đơn, mã nạp và mã nội dung chuyển khoản. */
@Component
public class CodeGenerator {

    public static final String ORDER_REF_PREFIX = "DH";
    public static final String TOPUP_REF_PREFIX = "NAP";
    public static final String SUBMISSION_PREFIX = "TK";
    public static final String WITHDRAW_PREFIX = "RUT";

    /** Bỏ các ký tự dễ nhầm (0/O, 1/I) để khách gõ tay không sai. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int REF_LENGTH = 8;
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final ZoneId zone;

    public CodeGenerator(Clock clock, ShopProperties props) {
        this.clock = clock;
        this.zone = props.zone();
    }

    /** vd ORD-20261014103000-531. */
    public String orderCode() {
        return "ORD-" + ORDER_TIME.format(clock.instant().atZone(zone)) + "-" + (100 + random.nextInt(900));
    }

    /** Mã nạp 15 chữ số, vd 188155420977844. */
    public String topupCode() {
        StringBuilder sb = new StringBuilder().append(1 + random.nextInt(9));
        for (int i = 1; i < 15; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /** vd DH8K2P9QXA / NAP8K2P9QXA. */
    public String payRef(String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < REF_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
