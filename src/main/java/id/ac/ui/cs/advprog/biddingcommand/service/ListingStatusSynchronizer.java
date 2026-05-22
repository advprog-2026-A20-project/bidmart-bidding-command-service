package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ListingStatusSynchronizer {

    private static final List<AuctionStatus> BIDDABLE_AUCTION_STATUSES = List.of(
        AuctionStatus.ACTIVE,
        AuctionStatus.EXTENDED
    );

    private static final List<ListingStatus> BIDDABLE_LISTING_STATUSES = List.of(
        ListingStatus.ACTIVE,
        ListingStatus.EXTENDED
    );

    private final Clock clock;

    public ListingStatusSynchronizer(Clock clock) {
        this.clock = clock;
    }

    List<AuctionStatus> biddableAuctionStatuses() {
        return BIDDABLE_AUCTION_STATUSES;
    }

    boolean isAuctionBiddable(AuctionStatus status) {
        return BIDDABLE_AUCTION_STATUSES.contains(status);
    }

    boolean isListingBiddable(ListingStatus status) {
        return BIDDABLE_LISTING_STATUSES.contains(status);
    }

    ListingStatus toListingStatus(AuctionStatus auctionStatus) {
        return switch (auctionStatus) {
            case DRAFT -> ListingStatus.DRAFT;
            case ACTIVE -> ListingStatus.ACTIVE;
            case EXTENDED -> ListingStatus.EXTENDED;
            case CLOSED -> ListingStatus.CLOSED;
            case WON -> ListingStatus.WON;
            case UNSOLD -> ListingStatus.UNSOLD;
            case CANCELLED -> ListingStatus.CANCELLED;
        };
    }

    void syncListingStatus(Auction auction) {
        if (auction == null || auction.getListing() == null || auction.getStatus() == null) {
            return;
        }

        ListingStatus targetStatus = toListingStatus(auction.getStatus());
        if (auction.getListing().getStatus() == targetStatus) {
            return;
        }

        auction.getListing().setStatus(targetStatus);
        auction.getListing().setUpdatedAt(Instant.now(clock));
    }
}
