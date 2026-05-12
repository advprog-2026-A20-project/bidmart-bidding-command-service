package id.ac.ui.cs.advprog.backend.service;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletInternalFundsRequest(
    UUID userId,
    UUID auctionId,
    BigDecimal amount
) {
}
