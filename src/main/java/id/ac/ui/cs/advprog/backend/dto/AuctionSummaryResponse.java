package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionSummaryResponse(
    UUID id,
    UUID listingId,
    String title,
    String description,
    UUID sellerId,
    String sellerEmail,
    BigDecimal currentPrice,
    BigDecimal startingPrice,
    BigDecimal minimumBidIncrement,
    AuctionStatus status,
    Instant createdAt,
    Instant startsAt,
    Instant endsAt,
    Integer extensionCount,
    long totalBids,
    BigDecimal nextMinimumBid
) {
}

