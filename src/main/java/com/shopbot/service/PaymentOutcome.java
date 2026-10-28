package com.shopbot.service;

import com.shopbot.domain.ShopOrder;
import com.shopbot.domain.Topup;

/** Kết quả xử lý 1 giao dịch chuyển khoản vào → dùng để báo tin cho khách/admin. */
public sealed interface PaymentOutcome {

    /** Đơn đã thanh toán đủ và được giao hàng. overpaid > 0 → phần dư đã cộng ví. */
    record OrderPaid(PaidOrder paid, long overpaid) implements PaymentOutcome {
    }

    /** Chuyển thiếu: đơn bị hủy, tiền đã chuyển cộng vào ví. */
    record OrderUnderpaid(ShopOrder order, long received, long balance) implements PaymentOutcome {
    }

    /** Tiền đến khi đơn đã hủy/hoàn tất: cộng vào ví. */
    record LatePayment(ShopOrder order, long received, long balance) implements PaymentOutcome {
    }

    /** Nạp tiền thành công. wasPending → còn tin QR cần xóa. */
    record TopupPaid(Topup topup, long credited, long balance, boolean wasPending) implements PaymentOutcome {
    }

    /** Không tìm thấy mã thanh toán → báo admin xử lý tay. */
    record Unmatched(long amount, String content) implements PaymentOutcome {
    }

    /** Giao dịch đã xử lý trước đó (SePay gửi lại webhook). */
    record Duplicate() implements PaymentOutcome {
    }
}
