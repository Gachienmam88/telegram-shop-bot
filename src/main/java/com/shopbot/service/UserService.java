package com.shopbot.service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.UserAccount;
import com.shopbot.repository.UserRepository;

@Service
public class UserService {

    /** Kết quả đăng ký: user + cờ "vừa tạo mới" (dùng để gán người giới thiệu). */
    public record Registration(UserAccount user, boolean created) {
    }

    private final UserRepository users;
    private final Clock clock;

    public UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /** Tạo user nếu chưa có, cập nhật username nếu đổi. Gọi ở mỗi update. */
    @Transactional
    public Registration register(long userId, String username) {
        return users.findById(userId)
                .map(u -> {
                    if (!Objects.equals(u.getUsername(), username)) {
                        u.setUsername(username);
                    }
                    return new Registration(u, false);
                })
                .orElseGet(() -> {
                    UserAccount u = new UserAccount();
                    u.setUserId(userId);
                    u.setUsername(username);
                    u.setLang(MessageService.DEFAULT_LANG);
                    u.setCreatedAt(clock.instant());
                    return new Registration(users.save(u), true);
                });
    }

    @Transactional
    public void setLanguage(long userId, String lang) {
        users.findById(userId).ifPresent(u -> u.setLang(MessageService.normalize(lang)));
    }

    /** Lưu thông tin nhận tiền (dùng lại khi rút). */
    @Transactional
    public void setBankInfo(long userId, String bankName, String accountNumber, String accountHolder) {
        UserAccount u = get(userId);
        u.setBankName(bankName);
        u.setBankAccountNumber(accountNumber);
        u.setBankAccountHolder(accountHolder);
    }

    /** Gán người giới thiệu: chỉ cho user mới, không tự giới thiệu, người giới thiệu phải tồn tại. */
    @Transactional
    public boolean assignReferrer(long userId, long referrerId) {
        if (userId == referrerId || !users.existsById(referrerId)) {
            return false;
        }
        UserAccount u = users.findById(userId).orElse(null);
        if (u == null || u.getReferrerId() != null) {
            return false;
        }
        u.setReferrerId(referrerId);
        return true;
    }

    @Transactional(readOnly = true)
    public UserAccount get(long userId) {
        return users.findById(userId).orElseThrow(() -> new ShopException("common.error"));
    }

    @Transactional(readOnly = true)
    public List<UserAccount> all() {
        return users.findAll();
    }

    @Transactional(readOnly = true)
    public long count() {
        return users.count();
    }
}
