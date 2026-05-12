package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuctionDetailResponse(
    UUID id,
    UUID listingId,
    String title,
    String description,
    UUID sellerId,
    String sellerEmail,
    BigDecimal currentPrice,
    BigDecimal startingPrice,
    BigDecimal reservePrice,
    BigDecimal minimumBidIncrement,
    AuctionStatus status,
    Instant createdAt,
    Instant startsAt,
    Instant endsAt,
    Instant closedAt,
    Long durationMinutes,
    Integer extensionCount,
    long totalBids,
    BigDecimal nextMinimumBid,
    boolean reserveMet,
    boolean biddable,
    BidResponse leadingBid,
    BidResponse winningBid,
    List<BidResponse> bidHistory
) {
}

