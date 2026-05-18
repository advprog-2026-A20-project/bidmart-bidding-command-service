package id.ac.ui.cs.advprog.backend.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletClient {

    void hold(UUID userId, UUID auctionId, BigDecimal amount);

    void release(UUID userId, UUID auctionId, BigDecimal amount);

    void capture(UUID userId, UUID auctionId, BigDecimal amount);
}
