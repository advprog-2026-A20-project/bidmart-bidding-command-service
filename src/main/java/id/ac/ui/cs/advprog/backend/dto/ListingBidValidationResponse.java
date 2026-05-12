package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.ListingStatus;
import java.time.Instant;
import java.util.UUID;

public record ListingBidValidationResponse(
    UUID listingId,
    boolean active,
    boolean biddable,
    String message,
    ListingStatus listingStatus,
    AuctionStatus auctionStatus,
    Instant endsAt
) {
}
