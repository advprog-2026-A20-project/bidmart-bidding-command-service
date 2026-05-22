package id.ac.ui.cs.advprog.biddingcommand.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BiddingCommandServicePlaceBidTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:15:30Z");
    private static final BigDecimal STARTING_PRICE = new BigDecimal("1000.00");
    private static final BigDecimal BID_1200 = new BigDecimal("1200.00");
    private static final BigDecimal BID_1400 = new BigDecimal("1400.00");
    private static final UUID AUCTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID LISTING_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SELLER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUYER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID OTHER_BUYER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID BID_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

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

    @Test
    void placeBidShouldHoldFundsPersistBidUpdateListingAndPublishEvent() {
        User buyer = buyer(BUYER_ID);
        Auction auction = activeAuction(seller(), ListingStatus.ACTIVE, AuctionStatus.ACTIVE, NOW.plusSeconds(600));
        AtomicReference<Bid> savedBidRef = new AtomicReference<>();
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(BID_ID);
            savedBidRef.set(bid);
            return bid;
        });
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID))
            .thenAnswer(invocation -> savedBidRef.get() == null ? List.of() : List.of(savedBidRef.get()));

        biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), BUYER_ID);

        InOrder inOrder = inOrder(walletClient, bidRepository, auctionRepository, eventPublisher);
        inOrder.verify(walletClient).holdFunds(BUYER_ID, AUCTION_ID, BID_1200);
        inOrder.verify(bidRepository).save(any(Bid.class));
        inOrder.verify(auctionRepository).save(auction);
        inOrder.verify(eventPublisher).publishEvent(any(Object.class));

        ArgumentCaptor<Bid> bidCaptor = ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).save(bidCaptor.capture());
        Bid savedBid = bidCaptor.getValue();
        assertEquals(BID_1200, savedBid.getAmount());
        assertEquals(1L, savedBid.getSequenceNumber());
        assertEquals(NOW, savedBid.getSubmittedAt());
        assertEquals(BID_1200, auction.getListing().getPrice());
        assertEquals(NOW, auction.getListing().getUpdatedAt());
        assertEquals(2L, auction.getNextBidSequence());
    }

    @Test
    void placeBidWhenRequestIsNullShouldThrowBadRequest() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, null, BUYER_ID)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Bid amount is required", exception.getReason());
    }

    @Test
    void placeBidWhenAmountIsNullShouldThrowBadRequest() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(null), BUYER_ID)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Bid amount is required", exception.getReason());
    }

    @Test
    void placeBidWhenAmountIsZeroOrNegativeShouldThrowBadRequest() {
        ResponseStatusException zeroException = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BigDecimal.ZERO), BUYER_ID)
        );
        ResponseStatusException negativeException = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(new BigDecimal("-1.00")), BUYER_ID)
        );

        assertEquals(HttpStatus.BAD_REQUEST, zeroException.getStatusCode());
        assertEquals("Bid amount must be positive", zeroException.getReason());
        assertEquals(HttpStatus.BAD_REQUEST, negativeException.getStatusCode());
        assertEquals("Bid amount must be positive", negativeException.getReason());
    }

    @Test
    void placeBidWhenBuyerNotFoundShouldThrowUnauthorized() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), BUYER_ID)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("User not found", exception.getReason());
    }

    @Test
    void placeBidWhenUserIsNotBuyerShouldThrowForbidden() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(seller()));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), BUYER_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Only BUYER can place bids", exception.getReason());
    }

    @Test
    void placeBidWhenAuctionNotFoundShouldThrowNotFound() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer(BUYER_ID)));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), BUYER_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Auction not found", exception.getReason());
    }

    @Test
    void placeBidWhenListingNotBiddableShouldThrowConflict() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer(BUYER_ID)));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID))
            .thenReturn(Optional.of(activeAuction(seller(), ListingStatus.DRAFT, AuctionStatus.ACTIVE, NOW.plusSeconds(600))));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), BUYER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void placeBidWhenAuctionNotBiddableShouldThrowConflict() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer(BUYER_ID)));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID))
            .thenReturn(Optional.of(activeAuction(seller(), ListingStatus.ACTIVE, AuctionStatus.DRAFT, NOW.plusSeconds(600))));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), BUYER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Auction is not accepting bids", exception.getReason());
    }

    @Test
    void placeBidWhenAuctionHasNoEndTimeShouldThrowConflict() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer(BUYER_ID)));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID))
            .thenReturn(Optional.of(activeAuction(seller(), ListingStatus.ACTIVE, AuctionStatus.ACTIVE, null)));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), BUYER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Auction has no valid end time", exception.getReason());
    }

    @Test
    void placeBidWhenSellerBidsOwnAuctionShouldThrowForbidden() {
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(buyer(SELLER_ID)));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID))
            .thenReturn(Optional.of(activeAuction(sellerWithId(SELLER_ID), ListingStatus.ACTIVE, AuctionStatus.ACTIVE, NOW.plusSeconds(600))));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), SELLER_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Seller cannot bid on their own auction", exception.getReason());
    }

    @Test
    void placeBidWhenAmountBelowMinimumShouldThrowConflict() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer(BUYER_ID)));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID))
            .thenReturn(Optional.of(activeAuction(seller(), ListingStatus.ACTIVE, AuctionStatus.ACTIVE, NOW.plusSeconds(600))));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID))
            .thenReturn(Optional.of(previousLeader(otherBuyer(), BID_1200, 1L)));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(new BigDecimal("1250.00")), BUYER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Bid must be at least 1300.00", exception.getReason());
    }

    @Test
    void placeBidWhenPreviousLeaderExistsShouldReleasePreviousLeaderFunds() {
        User buyer = buyer(BUYER_ID);
        Auction auction = activeAuction(seller(), ListingStatus.ACTIVE, AuctionStatus.ACTIVE, NOW.plusSeconds(600));
        Bid previousLeader = previousLeader(otherBuyer(), BID_1200, 1L);
        AtomicReference<Bid> savedBidRef = new AtomicReference<>();
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.of(previousLeader));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(BID_ID);
            savedBidRef.set(bid);
            return bid;
        });
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID))
            .thenAnswer(invocation -> savedBidRef.get() == null ? List.of(previousLeader) : List.of(previousLeader, savedBidRef.get()));

        biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1400), BUYER_ID);

        verify(walletClient).holdFunds(BUYER_ID, AUCTION_ID, BID_1400);
        verify(walletClient).releaseFunds(OTHER_BUYER_ID, AUCTION_ID, BID_1200);
    }

    @Test
    void placeBidWhenSameBidderIsPreviousLeaderShouldOnlyHoldDifference() {
        User buyer = buyer(BUYER_ID);
        Auction auction = activeAuction(seller(), ListingStatus.ACTIVE, AuctionStatus.ACTIVE, NOW.plusSeconds(600));
        Bid previousLeader = previousLeader(buyer, BID_1200, 1L);
        AtomicReference<Bid> savedBidRef = new AtomicReference<>();
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.of(previousLeader));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(BID_ID);
            savedBidRef.set(bid);
            return bid;
        });
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID))
            .thenAnswer(invocation -> savedBidRef.get() == null ? List.of(previousLeader) : List.of(previousLeader, savedBidRef.get()));

        biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1400), BUYER_ID);

        verify(walletClient).holdFunds(BUYER_ID, AUCTION_ID, new BigDecimal("200.00"));
        verify(walletClient, never()).releaseFunds(BUYER_ID, AUCTION_ID, BID_1200);
    }

    @Test
    void placeBidNearAuctionEndShouldExtendAuctionAndSetStatusExtended() {
        User buyer = buyer(BUYER_ID);
        Auction auction = activeAuction(seller(), ListingStatus.ACTIVE, AuctionStatus.ACTIVE, NOW.plusSeconds(30));
        AtomicReference<Bid> savedBidRef = new AtomicReference<>();
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(AUCTION_ID)).thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(BID_ID);
            savedBidRef.set(bid);
            return bid;
        });
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID))
            .thenAnswer(invocation -> savedBidRef.get() == null ? List.of() : List.of(savedBidRef.get()));

        biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), BUYER_ID);

        assertEquals(AuctionStatus.EXTENDED, auction.getStatus());
        assertEquals(NOW.plusSeconds(120), auction.getEndsAt());
        assertEquals(1, auction.getExtensionCount());
        assertEquals(ListingStatus.EXTENDED, auction.getListing().getStatus());
    }

    @Test
    void placeBidWhenAuctionExpiredShouldCloseAuctionBeforeRejectingOrProcessingAccordingToExistingBehavior() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer(BUYER_ID)));
        Auction auction = activeAuction(seller(), ListingStatus.ACTIVE, AuctionStatus.ACTIVE, NOW.minusSeconds(1));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.placeBid(AUCTION_ID, new BidPlaceRequest(BID_1200), BUYER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Auction is not accepting bids", exception.getReason());
        assertEquals(AuctionStatus.CLOSED, auction.getStatus());
        assertEquals(ListingStatus.CLOSED, auction.getListing().getStatus());
        verify(auctionRepository).save(auction);
        verify(walletClient, never()).holdFunds(any(), any(), any());
        verify(bidRepository, never()).save(any(Bid.class));
    }

    private User seller() {
        return sellerWithId(SELLER_ID);
    }

    private User sellerWithId(UUID sellerId) {
        return User.builder()
            .id(sellerId)
            .email("seller@bidmart.test")
            .passwordHash("hash")
            .role(Role.SELLER)
            .createdAt(NOW.minusSeconds(3600))
            .build();
    }

    private User buyer(UUID buyerId) {
        return User.builder()
            .id(buyerId)
            .email("buyer@bidmart.test")
            .passwordHash("hash")
            .role(Role.BUYER)
            .createdAt(NOW.minusSeconds(3600))
            .build();
    }

    private User otherBuyer() {
        return User.builder()
            .id(OTHER_BUYER_ID)
            .email("other-buyer@bidmart.test")
            .passwordHash("hash")
            .role(Role.BUYER)
            .createdAt(NOW.minusSeconds(3600))
            .build();
    }

    private Auction activeAuction(User seller, ListingStatus listingStatus, AuctionStatus auctionStatus, Instant endsAt) {
        Listing listing = Listing.builder()
            .id(LISTING_ID)
            .title("Vintage Camera")
            .description("Well kept camera")
            .price(STARTING_PRICE)
            .category(ListingCategory.ELECTRONICS)
            .seller(seller)
            .status(listingStatus)
            .createdAt(NOW.minusSeconds(600))
            .updatedAt(NOW.minusSeconds(600))
            .build();
        return Auction.builder()
            .id(AUCTION_ID)
            .listing(listing)
            .status(auctionStatus)
            .startingPrice(STARTING_PRICE)
            .reservePrice(new BigDecimal("1500.00"))
            .minimumBidIncrement(new BigDecimal("100.00"))
            .durationMinutes(60L)
            .nextBidSequence(1L)
            .extensionCount(0)
            .createdAt(NOW.minusSeconds(600))
            .activatedAt(NOW.minusSeconds(600))
            .startsAt(NOW.minusSeconds(600))
            .endsAt(endsAt)
            .build();
    }

    private Bid previousLeader(User bidder, BigDecimal amount, long sequenceNumber) {
        return Bid.builder()
            .id(UUID.randomUUID())
            .auction(Auction.builder().id(AUCTION_ID).build())
            .bidder(bidder)
            .amount(amount)
            .sequenceNumber(sequenceNumber)
            .submittedAt(NOW.minusSeconds(60))
            .build();
    }
}
