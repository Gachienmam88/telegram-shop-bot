package com.shopbot.domain;

/** Trạng thái 1 tài khoản trong kho. DELETED = đã xóa mềm (admin /delstock). */
public enum StockStatus {
    AVAILABLE, RESERVED, SOLD, DELETED
}
