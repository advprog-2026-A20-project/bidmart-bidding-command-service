package id.ac.ui.cs.advprog.biddingcommand.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletCommandRequest(
    UUID userId,
    UUID auctionId,
    BigDecimal amount
) {
}
