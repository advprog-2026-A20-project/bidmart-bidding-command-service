package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionCommandResponse(
    UUID auctionId,
    UUID sellerId,
    UUID listingId,
    AuctionStatus status,
    BigDecimal startingPrice,
    BigDecimal currentHighestBid,
    UUID currentHighestBidderId,
    Instant endsAt,
    Instant createdAt,
    Instant updatedAt
) {
}
