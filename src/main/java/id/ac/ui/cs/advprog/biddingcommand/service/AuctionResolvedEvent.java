package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import java.util.UUID;

public record AuctionResolvedEvent(
    UUID auctionId,
    AuctionStatus status
) {
}
