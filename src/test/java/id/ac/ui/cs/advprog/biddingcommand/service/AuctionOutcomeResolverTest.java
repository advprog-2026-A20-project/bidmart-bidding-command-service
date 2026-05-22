package id.ac.ui.cs.advprog.biddingcommand.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingCategory;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import id.ac.ui.cs.advprog.biddingcommand.repository.AuctionRepository;
import id.ac.ui.cs.advprog.biddingcommand.repository.BidRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AuctionOutcomeResolverTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID AUCTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SELLER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUYER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private WalletClient walletClient;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuctionOutcomeResolver auctionOutcomeResolver;

    @BeforeEach
    void setUp() {
        auctionOutcomeResolver = new AuctionOutcomeResolver(
            walletClient,
            auctionRepository,
            bidRepository,
            eventPublisher,
            new BidCalculator(),
            new ListingStatusSynchronizer(Clock.fixed(NOW, ZoneOffset.UTC))
        );
    }

    @Test
    void resolveClosedAuctionShouldDoNothingWhenAuctionIsNotClosed() {
        auctionOutcomeResolver.resolveClosedAuction(auction(AuctionStatus.ACTIVE));

        verify(bidRepository, never()).findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(any());
        verify(auctionRepository, never()).save(any(Auction.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resolveClosedAuctionShouldMarkAuctionWonWhenReserveIsMet() {
        Auction auction = auction(AuctionStatus.CLOSED);
        Bid leadingBid = bid("1600.00");
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID))
            .thenReturn(Optional.of(leadingBid));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auctionOutcomeResolver.resolveClosedAuction(auction);

        verify(walletClient).captureFunds(BUYER_ID, AUCTION_ID, new BigDecimal("1600.00"));
        verify(walletClient).creditFunds(SELLER_ID, AUCTION_ID, new BigDecimal("1600.00"));
        verify(auctionRepository).save(auction);
        verify(eventPublisher).publishEvent(any(AuctionResolvedEvent.class));
        assertEquals(AuctionStatus.WON, auction.getStatus());
        assertEquals(ListingStatus.WON, auction.getListing().getStatus());
    }

    @Test
    void resolveClosedAuctionShouldMarkAuctionUnsoldAndReleaseFundsWhenReserveIsNotMet() {
        Auction auction = auction(AuctionStatus.CLOSED);
        Bid leadingBid = bid("1400.00");
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID))
            .thenReturn(Optional.of(leadingBid));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auctionOutcomeResolver.resolveClosedAuction(auction);

        verify(walletClient).releaseFunds(BUYER_ID, AUCTION_ID, new BigDecimal("1400.00"));
        verify(walletClient, never()).captureFunds(any(), any(), any());
        verify(auctionRepository).save(auction);
        verify(eventPublisher).publishEvent(any(AuctionResolvedEvent.class));
        assertEquals(AuctionStatus.UNSOLD, auction.getStatus());
        assertEquals(ListingStatus.UNSOLD, auction.getListing().getStatus());
    }

    @Test
    void resolveClosedAuctionShouldMarkAuctionUnsoldWithoutWalletReleaseWhenNoBidExists() {
        Auction auction = auction(AuctionStatus.CLOSED);
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID))
            .thenReturn(Optional.empty());
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auctionOutcomeResolver.resolveClosedAuction(auction);

        verify(walletClient, never()).releaseFunds(any(), any(), any());
        verify(walletClient, never()).captureFunds(any(), any(), any());
        verify(auctionRepository).save(auction);
        verify(eventPublisher).publishEvent(any(AuctionResolvedEvent.class));
        assertEquals(AuctionStatus.UNSOLD, auction.getStatus());
        assertEquals(ListingStatus.UNSOLD, auction.getListing().getStatus());
    }

    private Auction auction(AuctionStatus status) {
        return Auction.builder()
            .id(AUCTION_ID)
            .listing(Listing.builder()
                .id(UUID.randomUUID())
                .title("Camera")
                .description("Vintage camera")
                .price(new BigDecimal("1000.00"))
                .category(ListingCategory.ELECTRONICS)
                .status(ListingStatus.CLOSED)
                .seller(User.builder()
                    .id(SELLER_ID)
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
            .nextBidSequence(1L)
            .extensionCount(0)
            .createdAt(NOW)
            .closedAt(NOW)
            .build();
    }

    private Bid bid(String amount) {
        return Bid.builder()
            .id(UUID.randomUUID())
            .auction(auction(AuctionStatus.CLOSED))
            .bidder(User.builder()
                .id(BUYER_ID)
                .email("buyer@example.test")
                .passwordHash("hash")
                .role(Role.BUYER)
                .createdAt(NOW)
                .build())
            .amount(new BigDecimal(amount))
            .sequenceNumber(1L)
            .submittedAt(NOW)
            .build();
    }
}
