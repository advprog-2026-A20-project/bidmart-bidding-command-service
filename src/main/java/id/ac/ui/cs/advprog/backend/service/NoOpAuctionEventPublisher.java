package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.Bid;
import org.springframework.stereotype.Service;

@Service
public class NoOpAuctionEventPublisher implements AuctionEventPublisher {

    @Override
    public void publishAuctionActivated(Auction auction) {
        // no-op until event bus/outbox integration is implemented
    }

    @Override
    public void publishBidPlaced(Auction auction, Bid bid, Bid previousLeadingBid) {
        // no-op until event bus/outbox integration is implemented
    }

    @Override
    public void publishAuctionResolved(Auction auction, Bid winningBid, boolean reserveMet) {
        // no-op until event bus/outbox integration is implemented
    }
}
