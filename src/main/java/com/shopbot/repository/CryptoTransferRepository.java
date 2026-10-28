package com.shopbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopbot.domain.CryptoTransfer;

public interface CryptoTransferRepository extends JpaRepository<CryptoTransfer, String> {
}
