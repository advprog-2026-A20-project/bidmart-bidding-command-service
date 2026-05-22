package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BidCalculator {

    private static final Comparator<Bid> LEADING_BID_COMPARATOR = Comparator
        .comparing(Bid::getAmount)
        .thenComparing(Bid::getSequenceNumber, Comparator.reverseOrder());

    Bid selectLeadingBid(List<Bid> bids) {
        return bids.stream().max(LEADING_BID_COMPARATOR).orElse(null);
    }

    BigDecimal calculateNextMinimumBid(Auction auction, Bid leadingBid) {
        if (leadingBid == null) {
            return auction.getStartingPrice();
        }
        return leadingBid.getAmount().add(auction.getMinimumBidIncrement());
    }

    boolean isReserveMet(Auction auction, Bid leadingBid) {
        return leadingBid != null && leadingBid.getAmount().compareTo(auction.getReservePrice()) >= 0;
    }
}
