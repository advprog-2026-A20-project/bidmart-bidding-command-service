package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import id.ac.ui.cs.advprog.biddingcommand.repository.AuctionRepository;
import id.ac.ui.cs.advprog.biddingcommand.repository.BidRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;

final class AuctionOutcomeResolver {

    private final WalletClient walletClient;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    AuctionOutcomeResolver(
        WalletClient walletClient,
        AuctionRepository auctionRepository,
        BidRepository bidRepository,
        ApplicationEventPublisher eventPublisher,
        Clock clock
    ) {
        this.walletClient = walletClient;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    void resolveClosedAuction(Auction auction) {
        if (auction.getStatus() != AuctionStatus.CLOSED) {
            return;
        }
        Bid leadingBid = bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(auction.getId())
            .orElse(null);

        boolean reserveMet = leadingBid != null && leadingBid.getAmount().compareTo(auction.getReservePrice()) >= 0;
        if (reserveMet) {
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

        syncListingStatus(auction);
        auctionRepository.save(auction);
        eventPublisher.publishEvent(new AuctionResolvedEvent(auction.getId(), auction.getStatus()));
    }

    private void syncListingStatus(Auction auction) {
        ListingStatus listingStatus = switch (auction.getStatus()) {
            case DRAFT -> ListingStatus.DRAFT;
            case ACTIVE -> ListingStatus.ACTIVE;
            case EXTENDED -> ListingStatus.EXTENDED;
            case CLOSED -> ListingStatus.CLOSED;
            case WON -> ListingStatus.WON;
            case UNSOLD -> ListingStatus.UNSOLD;
            case CANCELLED -> ListingStatus.CANCELLED;
        };
        if (auction.getListing().getStatus() != listingStatus) {
            auction.getListing().setStatus(listingStatus);
            auction.getListing().setUpdatedAt(Instant.now(clock));
        }
    }
}
