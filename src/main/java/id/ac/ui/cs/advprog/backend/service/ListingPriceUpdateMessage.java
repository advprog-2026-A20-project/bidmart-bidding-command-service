package id.ac.ui.cs.advprog.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingPriceUpdateMessage(
    UUID listingId,
    UUID auctionId,
    BigDecimal latestPrice,
    Instant submittedAt
) {
}
