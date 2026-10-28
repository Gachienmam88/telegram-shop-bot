package com.shopbot.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Clock chỉnh được thời gian trong test (vd tua nhanh 5 phút để test hết hạn đơn). */
public class MutableClock extends Clock {

    private volatile Instant now = Instant.parse("2026-10-14T03:00:00Z");

    public void advance(Duration d) {
        now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
