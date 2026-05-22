package id.ac.ui.cs.advprog.biddingcommand.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingCategory;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BidCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private BidCalculator bidCalculator;

    @BeforeEach
    void setUp() {
        bidCalculator = new BidCalculator();
    }

    @Test
    void selectLeadingBidShouldReturnNullWhenNoBidsExist() {
        assertNull(bidCalculator.selectLeadingBid(List.of()));
    }

    @Test
    void selectLeadingBidShouldReturnHighestAmount() {
        Bid leadingBid = bidCalculator.selectLeadingBid(List.of(
            bid("1100.00", 1L, "buyer-a@example.test"),
            bid("1500.00", 2L, "buyer-b@example.test"),
            bid("1300.00", 3L, "buyer-c@example.test")
        ));

        assertEquals(new BigDecimal("1500.00"), leadingBid.getAmount());
        assertEquals(2L, leadingBid.getSequenceNumber());
    }

    @Test
    void selectLeadingBidShouldPreserveCurrentTieBreakingBehavior() {
        Bid leadingBid = bidCalculator.selectLeadingBid(List.of(
            bid("1500.00", 3L, "late@example.test"),
            bid("1500.00", 1L, "early@example.test")
        ));

        assertEquals(1L, leadingBid.getSequenceNumber());
    }

    @Test
    void calculateNextMinimumBidShouldUseStartingPriceWhenNoLeadingBidExists() {
        assertEquals(new BigDecimal("1000.00"), bidCalculator.calculateNextMinimumBid(auction(), null));
    }

    @Test
    void calculateNextMinimumBidShouldUseLeadingBidAmountPlusIncrement() {
        assertEquals(
            new BigDecimal("1600.00"),
            bidCalculator.calculateNextMinimumBid(auction(), bid("1500.00", 1L, "buyer@example.test"))
        );
    }

    @Test
    void isReserveMetShouldReturnFalseWhenNoLeadingBidExists() {
        assertFalse(bidCalculator.isReserveMet(auction(), null));
    }

    @Test
    void isReserveMetShouldReturnFalseWhenLeadingBidIsBelowReserve() {
        assertFalse(bidCalculator.isReserveMet(auction(), bid("1499.99", 1L, "buyer@example.test")));
    }

    @Test
    void isReserveMetShouldReturnTrueWhenLeadingBidEqualsReserve() {
        assertTrue(bidCalculator.isReserveMet(auction(), bid("1500.00", 1L, "buyer@example.test")));
    }

    @Test
    void isReserveMetShouldReturnTrueWhenLeadingBidExceedsReserve() {
        assertTrue(bidCalculator.isReserveMet(auction(), bid("1600.00", 1L, "buyer@example.test")));
    }

    private Auction auction() {
        return Auction.builder()
            .id(UUID.randomUUID())
            .listing(Listing.builder()
                .id(UUID.randomUUID())
                .title("Camera")
                .description("Vintage camera")
                .price(new BigDecimal("1000.00"))
                .category(ListingCategory.ELECTRONICS)
                .status(ListingStatus.ACTIVE)
                .seller(User.builder()
                    .id(UUID.randomUUID())
                    .email("seller@example.test")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .createdAt(NOW)
                    .build())
                .createdAt(NOW)
                .build())
            .status(AuctionStatus.ACTIVE)
            .startingPrice(new BigDecimal("1000.00"))
            .reservePrice(new BigDecimal("1500.00"))
            .minimumBidIncrement(new BigDecimal("100.00"))
            .durationMinutes(60L)
            .nextBidSequence(1L)
            .extensionCount(0)
            .createdAt(NOW)
            .build();
    }

    private Bid bid(String amount, long sequenceNumber, String email) {
        return Bid.builder()
            .id(UUID.randomUUID())
            .auction(auction())
            .bidder(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hash")
                .role(Role.BUYER)
                .createdAt(NOW)
                .build())
            .amount(new BigDecimal(amount))
            .sequenceNumber(sequenceNumber)
            .submittedAt(NOW)
            .build();
    }
}
