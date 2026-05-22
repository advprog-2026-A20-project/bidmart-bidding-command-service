package id.ac.ui.cs.advprog.biddingcommand.service;

import java.math.BigDecimal;
import java.util.UUID;

public record BidPlacedEvent(
    UUID bidId,
    UUID auctionId,
    UUID bidderId,
    BigDecimal amount
) {
}
