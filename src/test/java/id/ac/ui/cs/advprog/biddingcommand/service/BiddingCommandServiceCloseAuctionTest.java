package id.ac.ui.cs.advprog.biddingcommand.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidResponse;
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
import id.ac.ui.cs.advprog.biddingcommand.repository.ListingRepository;
import id.ac.ui.cs.advprog.biddingcommand.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class BiddingCommandServiceCloseAuctionTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:15:30Z");
    private static final BigDecimal BID_1200 = new BigDecimal("1200.00");
    private static final BigDecimal BID_1600 = new BigDecimal("1600.00");
    private static final BigDecimal STARTING_PRICE = new BigDecimal("1000.00");
    private static final String BUYER_EMAIL = "buyer@example.test";
    private static final String OTHER_BIDDER_EMAIL = "other@example.test";
    private static final UUID SELLER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_SELLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID AUCTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID LISTING_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BUYER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID OTHER_BIDDER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletClient walletClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BiddingCommandService biddingCommandService;

    @BeforeEach
    void setUp() {
        biddingCommandService = new BiddingCommandService(
            auctionRepository,
            bidRepository,
            listingRepository,
            userRepository,
            walletClient,
            Clock.fixed(NOW, ZoneOffset.UTC),
            5L,
            eventPublisher
        );
    }

    private User seller() {
        return User.builder()
            .id(SELLER_ID)
            .email("seller@bidmart.test")
            .passwordHash("hash")
            .role(Role.SELLER)
            .createdAt(NOW.minusSeconds(3600))
            .build();
    }

    @Test
    void closeAuctionByOwnerAfterEndShouldCloseAuction() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.minusSeconds(1));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.empty());
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of());
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionDetailResponse response = biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID);

        assertEquals(AuctionStatus.UNSOLD, auction.getStatus());
        assertNotNull(auction.getClosedAt());
        assertEquals(ListingStatus.UNSOLD, auction.getListing().getStatus());
        assertEquals(AuctionStatus.UNSOLD, response.status());
    }

    @Test
    void closeAuctionByNonOwnerShouldThrowForbidden() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.minusSeconds(1));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(OTHER_SELLER_ID)).thenReturn(Optional.of(seller(OTHER_SELLER_ID)));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.closeAuction(AUCTION_ID, OTHER_SELLER_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Only the seller can manage this auction", exception.getReason());
    }

    @Test
    void closeAuctionWhenAuctionNotFoundShouldThrowNotFound() {
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Auction not found", exception.getReason());
    }

    @Test
    void closeAuctionWhenAuctionAlreadyClosedShouldThrowConflict() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.minusSeconds(1));
        auction.setStatus(AuctionStatus.WON);
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Auction is already closed", exception.getReason());
    }

    @Test
    void closeAuctionBeforeEndShouldThrowConflict() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.plusSeconds(60));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Auction cannot be closed before its scheduled end", exception.getReason());
    }

    @Test
    void closeAuctionWhenReserveMetShouldCaptureFundsAndSetWon() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.minusSeconds(1));
        Bid leadingBid = bid(otherBuyer(), BID_1600, 2L, OTHER_BIDDER_EMAIL);
        Bid lowerBid = bid(buyer(), BID_1200, 1L, BUYER_EMAIL);
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.of(leadingBid));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of(lowerBid, leadingBid));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionDetailResponse response = biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID);

        InOrder inOrder = inOrder(walletClient, auctionRepository, eventPublisher);
        inOrder.verify(walletClient).captureFunds(OTHER_BIDDER_ID, AUCTION_ID, BID_1600);
        inOrder.verify(walletClient).creditFunds(SELLER_ID, AUCTION_ID, BID_1600);
        assertEquals(AuctionStatus.WON, auction.getStatus());
        assertEquals(ListingStatus.WON, auction.getListing().getStatus());
        assertEquals(AuctionStatus.WON, response.status());
        assertTrue(response.reserveMet());
        assertEquals(2L, response.totalBids());
        assertNotNull(response.leadingBid());
        assertNotNull(response.winningBid());
        assertEquals(new BigDecimal("1700.00"), response.nextMinimumBid());
    }

    @Test
    void closeAuctionWhenReserveNotMetShouldReleaseFundsAndSetUnsold() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.minusSeconds(1));
        Bid leadingBid = bid(buyer(), BID_1200, 1L, BUYER_EMAIL);
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.of(leadingBid));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of(leadingBid));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionDetailResponse response = biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID);

        verify(walletClient).releaseFunds(BUYER_ID, AUCTION_ID, BID_1200);
        verify(walletClient, never()).captureFunds(any(), any(), any());
        assertEquals(AuctionStatus.UNSOLD, auction.getStatus());
        assertEquals(ListingStatus.UNSOLD, auction.getListing().getStatus());
        assertFalse(response.reserveMet());
        assertNotNull(response.leadingBid());
        assertNull(response.winningBid());
    }

    @Test
    void closeAuctionWhenNoBidShouldSetUnsoldWithoutWalletCaptureOrRelease() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.minusSeconds(1));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.empty());
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of());
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionDetailResponse response = biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID);

        verify(walletClient, never()).captureFunds(any(), any(), any());
        verify(walletClient, never()).releaseFunds(any(), any(), any());
        assertEquals(AuctionStatus.UNSOLD, auction.getStatus());
        assertEquals(AuctionStatus.UNSOLD, response.status());
        assertEquals(0L, response.totalBids());
        assertNull(response.leadingBid());
        assertNull(response.winningBid());
        assertEquals(STARTING_PRICE, response.nextMinimumBid());
    }

    @Test
    void closeAuctionShouldPublishAuctionResolvedEvent() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.minusSeconds(1));
        Bid leadingBid = bid(otherBuyer(), BID_1600, 2L, OTHER_BIDDER_EMAIL);
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.of(leadingBid));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of(leadingBid));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID);

        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void getAuctionDetailShouldReturnDetailWithLeadingBid() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.minusSeconds(1));
        Bid leadingBid = bid(otherBuyer(), BID_1600, 2L, OTHER_BIDDER_EMAIL);
        Bid lowerBid = bid(buyer(), BID_1200, 1L, BUYER_EMAIL);
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.of(leadingBid));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of(lowerBid, leadingBid));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionDetailResponse response = biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID);

        assertNotNull(response.leadingBid());
        assertEquals(OTHER_BIDDER_ID, response.leadingBid().bidderId());
        assertEquals(BID_1600, response.leadingBid().amount());
        assertEquals(BID_1600, response.currentPrice());
    }

    @Test
    void getBidHistoryShouldMarkOnlyLeadingBidAsWinningWhenAuctionNotUnsold() {
        Auction auction = activeAuction(seller(SELLER_ID), NOW.minusSeconds(1));
        Bid lowerBid = bid(buyer(), BID_1200, 1L, BUYER_EMAIL);
        Bid leadingBid = bid(otherBuyer(), BID_1600, 2L, OTHER_BIDDER_EMAIL);
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.of(leadingBid));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of(lowerBid, leadingBid));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuctionDetailResponse response = biddingCommandService.closeAuction(AUCTION_ID, SELLER_ID);

        assertEquals(2, response.bidHistory().size());
        BidResponse first = response.bidHistory().get(0);
        BidResponse second = response.bidHistory().get(1);
        assertFalse(first.winning());
        assertTrue(second.winning());
    }

    @Test
    void getAuctionDetailWhenAuctionExpiredShouldCloseAuctionBeforeReturningDetail() {
        Auction auction = activeAuction(seller(), NOW.minusSeconds(1));
        auction.setStatus(AuctionStatus.EXTENDED);
        auction.getListing().setStatus(ListingStatus.EXTENDED);

        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID))
            .thenReturn(Optional.of(auction));

        when(auctionRepository.save(any(Auction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID))
            .thenReturn(List.of());

        AuctionDetailResponse response = biddingCommandService.getAuctionDetail(AUCTION_ID);

        assertEquals(AuctionStatus.CLOSED, auction.getStatus());
        assertEquals(ListingStatus.CLOSED, auction.getListing().getStatus());
        assertEquals(AuctionStatus.CLOSED, response.status());
        assertFalse(response.biddable());
        verify(auctionRepository).save(auction);
    }

    private User seller(UUID sellerId) {
        return User.builder()
            .id(sellerId)
            .email("seller@bidmart.test")
            .passwordHash("hash")
            .role(Role.SELLER)
            .createdAt(NOW.minusSeconds(3600))
            .build();
    }

    private User buyer() {
        return user(BUYER_ID, BUYER_EMAIL);
    }

    private User otherBuyer() {
        return user(OTHER_BIDDER_ID, OTHER_BIDDER_EMAIL);
    }

    private User user(UUID id, String email) {
        return User.builder()
            .id(id)
            .email(email)
            .passwordHash("hash")
            .role(Role.BUYER)
            .createdAt(NOW.minusSeconds(3600))
            .build();
    }

    private Auction activeAuction(User seller, Instant endsAt) {
        Listing listing = Listing.builder()
            .id(LISTING_ID)
            .title("Vintage Camera")
            .description("Well kept camera")
            .price(STARTING_PRICE)
            .category(ListingCategory.ELECTRONICS)
            .seller(seller)
            .status(ListingStatus.ACTIVE)
            .createdAt(NOW.minusSeconds(600))
            .updatedAt(NOW.minusSeconds(600))
            .build();
        return Auction.builder()
            .id(AUCTION_ID)
            .listing(listing)
            .status(AuctionStatus.ACTIVE)
            .startingPrice(STARTING_PRICE)
            .reservePrice(new BigDecimal("1500.00"))
            .minimumBidIncrement(new BigDecimal("100.00"))
            .durationMinutes(60L)
            .nextBidSequence(3L)
            .extensionCount(0)
            .createdAt(NOW.minusSeconds(600))
            .activatedAt(NOW.minusSeconds(600))
            .startsAt(NOW.minusSeconds(600))
            .endsAt(endsAt)
            .build();
    }

    private Bid bid(User bidder, BigDecimal amount, long sequenceNumber, String email) {
        bidder.setEmail(email);
        return Bid.builder()
            .id(UUID.randomUUID())
            .auction(Auction.builder().id(AUCTION_ID).build())
            .bidder(bidder)
            .amount(amount)
            .sequenceNumber(sequenceNumber)
            .submittedAt(NOW.minusSeconds(60 * sequenceNumber))
            .build();
    }
}
