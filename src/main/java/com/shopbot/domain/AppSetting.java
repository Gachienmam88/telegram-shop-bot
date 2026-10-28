package com.shopbot.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cấu hình thay đổi được lúc chạy (vd affiliate_percent). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_settings")
public class AppSetting {

    @Id
    private String key;

    private String value;

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
