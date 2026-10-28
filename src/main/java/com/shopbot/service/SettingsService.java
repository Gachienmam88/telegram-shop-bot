package com.shopbot.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopbot.domain.AppSetting;
import com.shopbot.repository.AppSettingRepository;

/** Cấu hình runtime lưu DB (admin đổi bằng lệnh, không cần restart). */
@Service
public class SettingsService {

    public static final String AFFILIATE_PERCENT = "affiliate_percent";

    private final AppSettingRepository repo;

    public SettingsService(AppSettingRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        return repo.findById(key)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getValue().trim());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    /** Giá trị số nguyên dài, empty nếu chưa có / không đọc được. */
    @Transactional(readOnly = true)
    public java.util.Optional<Long> getLong(String key) {
        return repo.findById(key).flatMap(s -> {
            try {
                return java.util.Optional.of(Long.parseLong(s.getValue().trim()));
            } catch (NumberFormatException e) {
                return java.util.Optional.empty();
            }
        });
    }

    @Transactional
    public void set(String key, String value) {
        repo.save(new AppSetting(key, value));
    }
}
