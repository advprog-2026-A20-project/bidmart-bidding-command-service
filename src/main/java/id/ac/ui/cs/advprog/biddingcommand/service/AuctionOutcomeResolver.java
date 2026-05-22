package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.repository.AuctionRepository;
import id.ac.ui.cs.advprog.biddingcommand.repository.BidRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AuctionOutcomeResolver {

    private final WalletClient walletClient;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BidCalculator bidCalculator;
    private final ListingStatusSynchronizer listingStatusSynchronizer;

    public AuctionOutcomeResolver(
        WalletClient walletClient,
        AuctionRepository auctionRepository,
        BidRepository bidRepository,
        ApplicationEventPublisher eventPublisher,
        BidCalculator bidCalculator,
        ListingStatusSynchronizer listingStatusSynchronizer
    ) {
        this.walletClient = walletClient;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.eventPublisher = eventPublisher;
        this.bidCalculator = bidCalculator;
        this.listingStatusSynchronizer = listingStatusSynchronizer;
    }

    void resolveClosedAuction(Auction auction) {
        if (auction.getStatus() != AuctionStatus.CLOSED) {
            return;
        }
        Bid leadingBid = bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(auction.getId()).orElse(null);

        if (bidCalculator.isReserveMet(auction, leadingBid)) {
            walletClient.captureFunds(leadingBid.getBidder().getId(), auction.getId(), leadingBid.getAmount());
            walletClient.creditFunds(
                auction.getListing().getSeller().getId(),
                auction.getId(),
                leadingBid.getAmount()
            );
            auction.setStatus(AuctionStatus.WON);
        } else {
            if (leadingBid != null) {
                walletClient.releaseFunds(leadingBid.getBidder().getId(), auction.getId(), leadingBid.getAmount());
            }
            auction.setStatus(AuctionStatus.UNSOLD);
        }

        listingStatusSynchronizer.syncListingStatus(auction);
        auctionRepository.save(auction);
        eventPublisher.publishEvent(new AuctionResolvedEvent(auction.getId(), auction.getStatus()));
    }
}
