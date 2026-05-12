package id.ac.ui.cs.advprog.backend.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletGateway {

    void holdFunds(UUID userId, UUID auctionId, BigDecimal amount);

    void releaseFunds(UUID userId, UUID auctionId, BigDecimal amount);

    void captureFunds(UUID userId, UUID auctionId, BigDecimal amount);
}

