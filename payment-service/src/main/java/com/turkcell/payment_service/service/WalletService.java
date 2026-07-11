package com.turkcell.payment_service.service;

import com.turkcell.payment_service.domain.entity.Wallet;
import com.turkcell.payment_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public boolean charge(UUID customerId, BigDecimal amount) {
        if (customerId == null) {
            return false;
        }
        Wallet wallet = walletRepository.findByCustomerId(customerId)
                .orElseGet(() -> createWallet(customerId));
        if (wallet.getBalance().compareTo(amount) < 0) {
            return false;
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
        return true;
    }

    @Transactional
    public void credit(UUID customerId, BigDecimal amount) {
        if (customerId == null || amount == null) {
            return;
        }
        Wallet wallet = walletRepository.findByCustomerId(customerId)
                .orElseGet(() -> createWallet(customerId));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);
    }

    private Wallet createWallet(UUID customerId) {
        Wallet wallet = new Wallet();
        wallet.setCustomerId(customerId);
        wallet.setBalance(BigDecimal.ZERO);
        return walletRepository.save(wallet);
    }
}
