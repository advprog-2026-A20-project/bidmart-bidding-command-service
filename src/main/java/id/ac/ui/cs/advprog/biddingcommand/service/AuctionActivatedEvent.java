package id.ac.ui.cs.advprog.biddingcommand.service;

import java.util.UUID;

public record AuctionActivatedEvent(
    UUID auctionId,
    UUID listingId
) {
}
