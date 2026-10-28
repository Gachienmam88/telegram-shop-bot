package com.shopbot.service;

import java.util.List;

import com.shopbot.domain.ShopOrder;

/** Đơn đã thanh toán + danh sách tài khoản giao cho khách. */
public record PaidOrder(ShopOrder order, List<String> accounts) {
}
