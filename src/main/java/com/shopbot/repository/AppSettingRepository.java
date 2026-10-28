package com.shopbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopbot.domain.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
