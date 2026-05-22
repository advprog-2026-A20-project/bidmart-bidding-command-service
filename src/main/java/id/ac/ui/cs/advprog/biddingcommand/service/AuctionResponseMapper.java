package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidResponse;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class AuctionResponseMapper {

    private static final Comparator<Bid> LEADING_BID_COMPARATOR = Comparator
        .comparing(Bid::getAmount)
        .thenComparing(Bid::getSequenceNumber, Comparator.reverseOrder());

    AuctionDetailResponse toDetailResponse(Auction auction, List<Bid> bids) {
        Bid leadingBid = selectLeadingBid(bids);
        boolean reserveMet = leadingBid != null && leadingBid.getAmount().compareTo(auction.getReservePrice()) >= 0;
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
            calculateNextMinimumBid(auction, leadingBid),
            reserveMet,
            isBiddableStatus(auction.getStatus()),
            leadingBid == null ? null : toBidResponse(auction, leadingBid, leadingBid),
            winningBid == null ? null : toBidResponse(auction, winningBid, leadingBid),
            bids.stream().map(bid -> toBidResponse(auction, bid, leadingBid)).toList()
        );
    }

    private BigDecimal calculateNextMinimumBid(Auction auction, Bid leadingBid) {
        if (leadingBid == null) {
            return auction.getStartingPrice();
        }
        return leadingBid.getAmount().add(auction.getMinimumBidIncrement());
    }

    private BidResponse toBidResponse(Auction auction, Bid bid, Bid leadingBid) {
        boolean isWinningBid = leadingBid != null
            && Objects.equals(leadingBid.getId(), bid.getId())
            && auction.getStatus() != AuctionStatus.UNSOLD;
        return new BidResponse(
            bid.getId(),
            bid.getBidder().getId(),
            bid.getBidder().getEmail(),
            bid.getAmount(),
            bid.getSequenceNumber(),
            bid.getSubmittedAt(),
            isWinningBid
        );
    }

    private Bid selectLeadingBid(List<Bid> bids) {
        return bids.stream().max(LEADING_BID_COMPARATOR).orElse(null);
    }

    private boolean isBiddableStatus(AuctionStatus status) {
        return status == AuctionStatus.ACTIVE || status == AuctionStatus.EXTENDED;
    }
}
