package com.shopbot.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.OrderStatus;
import com.shopbot.repository.OrderRepository;
import com.shopbot.repository.UserRepository;

/** Thống kê nhanh cho admin. */
@Service
public class StatsService {

    public record Stats(long users, long ordersToday, long revenueToday, long ordersTotal, long revenueTotal) {
    }

    private final OrderRepository orders;
    private final UserRepository users;
    private final ShopProperties props;
    private final Clock clock;

    public StatsService(OrderRepository orders, UserRepository users, ShopProperties props, Clock clock) {
        this.orders = orders;
        this.users = users;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        Instant startOfToday = LocalDate.now(clock.withZone(props.zone())).atStartOfDay(props.zone()).toInstant();
        return new Stats(
                users.count(),
                orders.countPaidSince(OrderStatus.PAID, startOfToday),
                orders.sumAmountPaidSince(OrderStatus.PAID, startOfToday),
                orders.countPaidSince(OrderStatus.PAID, Instant.EPOCH),
                orders.sumAmountPaidSince(OrderStatus.PAID, Instant.EPOCH));
    }
}
