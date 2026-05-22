package id.ac.ui.cs.advprog.biddingcommand.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingCategory;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuctionResponseMapperTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private AuctionResponseMapper auctionResponseMapper;

    @BeforeEach
    void setUp() {
        BidCalculator bidCalculator = new BidCalculator();
        ListingStatusSynchronizer listingStatusSynchronizer =
            new ListingStatusSynchronizer(Clock.fixed(NOW, ZoneOffset.UTC));
        auctionResponseMapper = new AuctionResponseMapper(bidCalculator, listingStatusSynchronizer);
    }

    @Test
    void toDetailResponseShouldReturnExpectedValuesWhenAuctionHasNoBids() {
        AuctionDetailResponse response = auctionResponseMapper.toDetailResponse(auction(AuctionStatus.DRAFT), List.of());

        assertEquals(new BigDecimal("1000.00"), response.currentPrice());
        assertEquals(new BigDecimal("1000.00"), response.nextMinimumBid());
        assertFalse(response.reserveMet());
        assertNull(response.leadingBid());
        assertNull(response.winningBid());
        assertTrue(response.bidHistory().isEmpty());
    }

    @Test
    void toDetailResponseShouldReturnLeadingBidAndCurrentPriceFromHighestBid() {
        Bid earlyBid = bid("1200.00", 1L, "buyer@example.test");
        Bid leadingBid = bid("1600.00", 2L, "winner@example.test");

        AuctionDetailResponse response = auctionResponseMapper.toDetailResponse(
            auction(AuctionStatus.ACTIVE),
            List.of(earlyBid, leadingBid)
        );

        assertEquals(new BigDecimal("1600.00"), response.currentPrice());
        assertEquals(new BigDecimal("1700.00"), response.nextMinimumBid());
        assertTrue(response.reserveMet());
        assertTrue(response.biddable());
        assertNotNull(response.leadingBid());
        assertEquals(new BigDecimal("1600.00"), response.leadingBid().amount());
        assertEquals("w****r@example.test", response.leadingBid().bidderEmail());
        assertNull(response.winningBid());
    }

    @Test
    void toDetailResponseShouldExposeWinningBidOnlyWhenAuctionIsWon() {
        Bid leadingBid = bid("1600.00", 2L, "winner@example.test");

        AuctionDetailResponse wonResponse = auctionResponseMapper.toDetailResponse(
            auction(AuctionStatus.WON),
            List.of(leadingBid)
        );
        AuctionDetailResponse unsoldResponse = auctionResponseMapper.toDetailResponse(
            auction(AuctionStatus.UNSOLD),
            List.of(leadingBid)
        );

        assertNotNull(wonResponse.winningBid());
        assertTrue(wonResponse.winningBid().winning());
        assertNull(unsoldResponse.winningBid());
        assertFalse(unsoldResponse.leadingBid().winning());
    }

    @Test
    void toDetailResponseShouldMaskBidderEmailAcrossEdgeCases() {
        AuctionDetailResponse response = auctionResponseMapper.toDetailResponse(
            auction(AuctionStatus.ACTIVE),
            List.of(
                bid("1100.00", 1L, "a@example.com"),
                bid("1200.00", 2L, "ab@example.com"),
                bid("1300.00", 3L, "bidder@example.com"),
                bid("1400.00", 4L, ""),
                bid("1500.00", 5L, "invalid-email")
            )
        );

        assertEquals("*@example.com", response.bidHistory().get(0).bidderEmail());
        assertEquals("a*@example.com", response.bidHistory().get(1).bidderEmail());
        assertEquals("b****r@example.com", response.bidHistory().get(2).bidderEmail());
        assertEquals("", response.bidHistory().get(3).bidderEmail());
        assertEquals("***", response.bidHistory().get(4).bidderEmail());
    }

    private Auction auction(AuctionStatus status) {
        return Auction.builder()
            .id(UUID.randomUUID())
            .listing(Listing.builder()
                .id(UUID.randomUUID())
                .title("Camera")
                .description("Vintage camera")
                .price(new BigDecimal("1000.00"))
                .category(ListingCategory.ELECTRONICS)
                .status(ListingStatus.DRAFT)
                .seller(User.builder()
                    .id(UUID.randomUUID())
                    .email("seller@example.test")
                    .passwordHash("hash")
                    .role(Role.SELLER)
                    .createdAt(NOW)
                    .build())
                .createdAt(NOW)
                .build())
            .status(status)
            .startingPrice(new BigDecimal("1000.00"))
            .reservePrice(new BigDecimal("1500.00"))
            .minimumBidIncrement(new BigDecimal("100.00"))
            .durationMinutes(60L)
            .extensionCount(0)
            .createdAt(NOW)
            .build();
    }

    private Bid bid(String amount, long sequenceNumber, String email) {
        return Bid.builder()
            .id(UUID.randomUUID())
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
