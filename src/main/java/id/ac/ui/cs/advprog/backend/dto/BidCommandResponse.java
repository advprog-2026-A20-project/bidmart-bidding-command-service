package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.BidStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BidCommandResponse(
    UUID auctionId,
    UUID bidId,
    UUID bidderId,
    BigDecimal amount,
    BidStatus bidStatus,
    AuctionStatus auctionStatus,
    BigDecimal currentHighestBid,
    UUID currentHighestBidderId,
    Instant createdAt
) {
}
