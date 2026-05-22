package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidResponse;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AuctionResponseMapper {

    private final BidCalculator bidCalculator;
    private final ListingStatusSynchronizer listingStatusSynchronizer;

    public AuctionResponseMapper(
        BidCalculator bidCalculator,
        ListingStatusSynchronizer listingStatusSynchronizer
    ) {
        this.bidCalculator = bidCalculator;
        this.listingStatusSynchronizer = listingStatusSynchronizer;
    }

    AuctionDetailResponse toDetailResponse(Auction auction, List<Bid> bids) {
        Bid leadingBid = bidCalculator.selectLeadingBid(bids);
        boolean reserveMet = bidCalculator.isReserveMet(auction, leadingBid);
        Bid winningBid = auction.getStatus() == AuctionStatus.WON ? leadingBid : null;
        BigDecimal currentPrice = leadingBid == null ? auction.getListing().getPrice() : leadingBid.getAmount();
        return new AuctionDetailResponse(
            auction.getId(),
            auction.getListing().getId(),
            auction.getListing().getTitle(),
            auction.getListing().getDescription(),
            auction.getListing().getSeller().getId(),
            auction.getListing().getSeller().getEmail(),
            currentPrice,
            auction.getStartingPrice(),
            auction.getReservePrice(),
            auction.getMinimumBidIncrement(),
            auction.getStatus(),
            auction.getCreatedAt(),
            auction.getStartsAt(),
            auction.getEndsAt(),
            auction.getClosedAt(),
            auction.getDurationMinutes(),
            auction.getExtensionCount(),
            bids.size(),
            bidCalculator.calculateNextMinimumBid(auction, leadingBid),
            reserveMet,
            listingStatusSynchronizer.isAuctionBiddable(auction.getStatus()),
            leadingBid == null ? null : toBidResponse(auction, leadingBid, leadingBid),
            winningBid == null ? null : toBidResponse(auction, winningBid, leadingBid),
            bids.stream().map(bid -> toBidResponse(auction, bid, leadingBid)).toList()
        );
    }

    private BidResponse toBidResponse(Auction auction, Bid bid, Bid leadingBid) {
        boolean isWinningBid = leadingBid != null
            && Objects.equals(leadingBid.getId(), bid.getId())
            && auction.getStatus() != AuctionStatus.UNSOLD;
        return new BidResponse(
            bid.getId(),
            bid.getBidder().getId(),
            maskEmail(bid.getBidder().getEmail()),
            bid.getAmount(),
            bid.getSequenceNumber(),
            bid.getSubmittedAt(),
            isWinningBid
        );
    }

    private String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        if (email.isBlank()) {
            return email;
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex != email.lastIndexOf('@') || atIndex == email.length() - 1) {
            return "***";
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() == 1) {
            return "*" + domain;
        }
        if (localPart.length() == 2) {
            return localPart.charAt(0) + "*" + domain;
        }

        return localPart.charAt(0)
            + "*".repeat(localPart.length() - 2)
            + localPart.charAt(localPart.length() - 1)
            + domain;
    }
}
