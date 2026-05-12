package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.Bid;

public interface AuctionEventPublisher {

    void publishAuctionActivated(Auction auction);

    void publishBidPlaced(Auction auction, Bid bid, Bid previousLeadingBid);

    void publishAuctionResolved(Auction auction, Bid winningBid, boolean reserveMet);
}

