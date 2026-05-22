package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class AuctionFactory {

    private static final BigDecimal DEFAULT_MINIMUM_INCREMENT = money("1.00");
    private static final long DEFAULT_DURATION_MINUTES = 60L;

    Auction buildDraftAuction(
        AuctionCreateRequest request,
        Listing listing,
        Instant createdAt,
        long durationMinutes
    ) {
        return Auction.builder()
            .listing(listing)
            .status(AuctionStatus.DRAFT)
            .startingPrice(normalizeMoney(request.startingPrice()))
            .reservePrice(normalizeMoney(request.reservePrice()))
            .minimumBidIncrement(normalizeMoney(resolveMinimumBidIncrement(request.minimumBidIncrement())))
            .durationMinutes(durationMinutes)
            .createdAt(createdAt)
            .nextBidSequence(1L)
            .extensionCount(0)
            .build();
    }

    long resolveDurationMinutes(Long durationMinutes) {
        return durationMinutes == null ? DEFAULT_DURATION_MINUTES : durationMinutes;
    }

    BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveMinimumBidIncrement(BigDecimal minimumBidIncrement) {
        return minimumBidIncrement == null ? DEFAULT_MINIMUM_INCREMENT : minimumBidIncrement;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}
