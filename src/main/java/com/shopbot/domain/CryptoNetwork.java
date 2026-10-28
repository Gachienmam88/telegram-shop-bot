package com.shopbot.domain;

/**
 * Mạng nhận USDT. Contract + decimals đã kiểm tra on-chain (2026-10-15).
 * confirmations: số block chờ thêm trước khi coi giao dịch là chắc chắn (TRON dùng only_confirmed của TronGrid).
 */
public enum CryptoNetwork {

    BSC("BNB Smart Chain (BEP20)", "0x55d398326f99059fF775485246999027B3197955", 18, 15,
            "https://bscscan.com/tx/"),
    TRON("TRON (TRC20)", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", 6, 0,
            "https://tronscan.org/#/transaction/"),
    POLYGON("Polygon (PoS)", "0xc2132D05D31c914a87C6611C10748AEb04B58e8F", 6, 30,
            "https://polygonscan.com/tx/");

    private final String displayName;
    private final String usdtContract;
    private final int decimals;
    private final int confirmations;
    private final String explorerTxUrl;

    CryptoNetwork(String displayName, String usdtContract, int decimals, int confirmations, String explorerTxUrl) {
        this.displayName = displayName;
        this.usdtContract = usdtContract;
        this.decimals = decimals;
        this.confirmations = confirmations;
        this.explorerTxUrl = explorerTxUrl;
    }

    public String displayName() {
        return displayName;
    }

    public String usdtContract() {
        return usdtContract;
    }

    public int decimals() {
        return decimals;
    }

    public int confirmations() {
        return confirmations;
    }

    public String explorerTxUrl(String txHash) {
        return explorerTxUrl + txHash;
    }
}
