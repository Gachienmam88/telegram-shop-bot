package com.shopbot.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.shopbot.config.ShopProperties;
import com.shopbot.domain.CryptoNetwork;

/** Địa chỉ ví nhận USDT theo mạng (từ .env). Mạng không có địa chỉ = tắt. */
@Component
public class CryptoWallets {

    private final ShopProperties.Crypto crypto;

    public CryptoWallets(ShopProperties props) {
        this.crypto = props.crypto();
    }

    public String address(CryptoNetwork network) {
        if (crypto == null) {
            return "";
        }
        String a = switch (network) {
            case BSC -> crypto.bscAddress();
            case TRON -> crypto.tronAddress();
            case POLYGON -> crypto.polygonAddress();
        };
        return a == null ? "" : a.trim();
    }

    public boolean enabled(CryptoNetwork network) {
        return !address(network).isEmpty();
    }

    /** Các mạng đang bật, theo thứ tự hiển thị. */
    public List<CryptoNetwork> enabledNetworks() {
        return Arrays.stream(CryptoNetwork.values()).filter(this::enabled).toList();
    }

    public boolean anyEnabled() {
        return !enabledNetworks().isEmpty();
    }

    /** Thời gian giữ đơn / lệnh nạp chờ chuyển USDT. */
    public int timeoutSeconds() {
        return crypto == null || crypto.timeoutSeconds() <= 0 ? 1800 : crypto.timeoutSeconds();
    }
}
