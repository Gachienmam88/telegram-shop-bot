package com.shopbot.util;

/** Validate số lượng mua và số tiền nạp do người dùng gõ. */
public final class InputValidators {

    /** Kết quả validate: ok + giá trị, hoặc lỗi. */
    public record Result(Status status, long value) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    public enum Status {
        OK, INVALID, OUT_OF_RANGE
    }

    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern
            .compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("^\\+?\\d[\\d\\s.-]{6,17}$");

    private InputValidators() {
    }

    /** Thông tin đăng nhập tài khoản: chấp nhận email hoặc số điện thoại. */
    public static boolean isAccountContact(String input) {
        String s = input == null ? "" : input.trim();
        return EMAIL.matcher(s).matches() || PHONE.matcher(s).matches();
    }

    /** Username tài khoản: 2..100 ký tự sau khi bỏ khoảng trắng thừa. */
    public static boolean isPassword(String input) {
        String s = input == null ? "" : input.trim();
        return s.length() >= 2 && s.length() <= 100;
    }

    /**
     * Số lượng: chỉ chấp nhận số nguyên dương (không dấu, không thập phân),
     * 1..maxAvailable.
     */
    public static Result quantity(String input, int maxAvailable) {
        String s = input == null ? "" : input.trim();
        if (!s.matches("\\d{1,6}")) {
            return new Result(Status.INVALID, 0);
        }
        long value = Long.parseLong(s);
        if (value < 1) {
            return new Result(Status.INVALID, 0);
        }
        if (value > maxAvailable) {
            return new Result(Status.OUT_OF_RANGE, value);
        }
        return new Result(Status.OK, value);
    }

    /**
     * Số tiền: cho phép dấu phân cách "100.000", "100,000", "100 000"; trong khoảng
     * [min, max].
     */
    public static Result amount(String input, long min, long max) {
        String s = input == null ? "" : input.trim().replaceAll("[.,\\s]", "");
        if (!s.matches("\\d{1,12}")) {
            return new Result(Status.INVALID, 0);
        }
        long value = Long.parseLong(s);
        if (value < min || value > max) {
            return new Result(Status.OUT_OF_RANGE, value);
        }
        return new Result(Status.OK, value);
    }
}
