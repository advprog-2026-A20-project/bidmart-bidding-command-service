package id.ac.ui.cs.advprog.biddingcommand.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingCategory;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import id.ac.ui.cs.advprog.biddingcommand.repository.UserRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionValidatorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID SELLER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUYER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private UserRepository userRepository;

    private AuctionValidator auctionValidator;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        auctionValidator = new AuctionValidator(
            userRepository,
            fixedClock,
            new BidCalculator(),
            new ListingStatusSynchronizer(fixedClock),
            20160L
        );
    }

    @Test
    void validateAuctionRequestShouldRejectNullRequest() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.validateAuctionRequest(null, 60L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Auction request is required", exception.getReason());
    }

    @Test
    void validateAuctionRequestShouldRejectBlankTitleAndDescription() {
        assertBadRequest(
            invalidAuctionRequest("   ", "Description", new BigDecimal("1000.00"), new BigDecimal("1500.00"),
                new BigDecimal("100.00")),
            60L,
            "Title is required"
        );
        assertBadRequest(
            invalidAuctionRequest("Camera", "   ", new BigDecimal("1000.00"), new BigDecimal("1500.00"),
                new BigDecimal("100.00")),
            60L,
            "Description is required"
        );
    }

    @Test
    void validateAuctionRequestShouldRejectInvalidStartingPrice() {
        assertBadRequest(
            invalidAuctionRequest("Camera", "Description", null, new BigDecimal("1500.00"), new BigDecimal("100.00")),
            60L,
            "Starting price must be positive"
        );
        assertBadRequest(
            invalidAuctionRequest("Camera", "Description", BigDecimal.ZERO, new BigDecimal("1500.00"),
                new BigDecimal("100.00")),
            60L,
            "Starting price must be positive"
        );
        assertBadRequest(
            invalidAuctionRequest("Camera", "Description", new BigDecimal("-1.00"), new BigDecimal("1500.00"),
                new BigDecimal("100.00")),
            60L,
            "Starting price must be positive"
        );
    }

    @Test
    void validateAuctionRequestShouldRejectInvalidReservePriceAndDuration() {
        assertBadRequest(
            invalidAuctionRequest("Camera", "Description", new BigDecimal("1000.00"), null, new BigDecimal("100.00")),
            60L,
            "Reserve price must be positive"
        );
        assertBadRequest(
            invalidAuctionRequest("Camera", "Description", new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal("100.00")),
            60L,
            "Reserve price must be positive"
        );
        assertBadRequest(
            invalidAuctionRequest("Camera", "Description", new BigDecimal("1000.00"), new BigDecimal("999.99"),
                new BigDecimal("100.00")),
            60L,
            "Reserve price must be greater than or equal to starting price"
        );
        assertBadRequest(validAuctionRequest(), 0L, "Duration must be between 1 and 20160 minutes");
        assertBadRequest(validAuctionRequest(), 20161L, "Duration must be between 1 and 20160 minutes");
    }

    @Test
    void validateAuctionRequestShouldRejectInvalidMinimumIncrement() {
        assertBadRequest(
            invalidAuctionRequest("Camera", "Description", new BigDecimal("1000.00"), new BigDecimal("1500.00"),
                BigDecimal.ZERO),
            60L,
            "Minimum bid increment must be positive"
        );
        assertBadRequest(
            invalidAuctionRequest("Camera", "Description", new BigDecimal("1000.00"), new BigDecimal("1500.00"),
                new BigDecimal("-1.00")),
            60L,
            "Minimum bid increment must be positive"
        );
    }

    @Test
    void loadBuyerAndLoadSellerShouldEnforceRoleValidation() {
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, Role.SELLER)));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(user(SELLER_ID, Role.BUYER)));

        ResponseStatusException buyerException = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.loadBuyer(BUYER_ID)
        );
        ResponseStatusException sellerException = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.loadSeller(SELLER_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, buyerException.getStatusCode());
        assertEquals("Only BUYER can place bids", buyerException.getReason());
        assertEquals(HttpStatus.FORBIDDEN, sellerException.getStatusCode());
        assertEquals("Only SELLER can manage auctions", sellerException.getReason());
    }

    @Test
    void ensureSellerOwnsAuctionShouldRejectDifferentSeller() {
        UUID otherSellerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(userRepository.findById(otherSellerId)).thenReturn(Optional.of(user(otherSellerId, Role.SELLER)));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.ensureSellerOwnsAuction(
                auction(AuctionStatus.DRAFT, ListingStatus.DRAFT),
                otherSellerId
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Only the seller can manage this auction", exception.getReason());
    }

    @Test
    void validateBidRequestShouldRejectNullZeroAndNegativeAmount() {
        assertBadBidRequest(null, "Bid amount is required");
        assertBadBidRequest(new BidPlaceRequest(null), "Bid amount is required");
        assertBadBidRequest(new BidPlaceRequest(BigDecimal.ZERO), "Bid amount must be positive");
        assertBadBidRequest(new BidPlaceRequest(new BigDecimal("-1.00")), "Bid amount must be positive");
    }

    @Test
    void ensureAuctionAcceptsBidShouldAllowValidAuctionAndRejectInvalidCases() {
        assertDoesNotThrow(() ->
            auctionValidator.ensureAuctionAcceptsBid(
                auction(AuctionStatus.ACTIVE, ListingStatus.ACTIVE),
                BUYER_ID
            )
        );

        ResponseStatusException statusException = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.ensureAuctionAcceptsBid(
                auction(AuctionStatus.DRAFT, ListingStatus.ACTIVE),
                BUYER_ID
            )
        );
        ResponseStatusException sellerException = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.ensureAuctionAcceptsBid(
                auction(AuctionStatus.ACTIVE, ListingStatus.ACTIVE),
                SELLER_ID
            )
        );

        assertEquals("Auction is not accepting bids", statusException.getReason());
        assertEquals("Seller cannot bid on their own auction", sellerException.getReason());
    }

    @Test
    void ensureListingAcceptsBidValidateBidAmountAndManualClosureShouldEnforceRules() {
        ResponseStatusException listingException = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.ensureListingAcceptsBid(auction(AuctionStatus.ACTIVE, ListingStatus.DRAFT))
        );
        ResponseStatusException amountException = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.validateBidAmountAgainstMinimum(
                auction(AuctionStatus.ACTIVE, ListingStatus.ACTIVE),
                bid("1200.00"),
                new BigDecimal("1250.00")
            )
        );
        ResponseStatusException closureException = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.validateManualClosureAllowed(
                auction(AuctionStatus.ACTIVE, ListingStatus.ACTIVE),
                NOW
            )
        );

        assertEquals("Listing is not accepting bids", listingException.getReason());
        assertEquals("Bid must be at least 1300.00", amountException.getReason());
        assertEquals("Auction cannot be closed before its scheduled end", closureException.getReason());
        assertDoesNotThrow(() ->
            auctionValidator.validateManualClosureAllowed(
                endedAuction(AuctionStatus.ACTIVE, ListingStatus.ACTIVE),
                NOW
            )
        );
    }

    private void assertBadRequest(AuctionCreateRequest request, long durationMinutes, String message) {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.validateAuctionRequest(request, durationMinutes)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(message, exception.getReason());
    }

    private void assertBadBidRequest(BidPlaceRequest request, String message) {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> auctionValidator.validateBidRequest(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(message, exception.getReason());
    }

    private AuctionCreateRequest validAuctionRequest() {
        return invalidAuctionRequest(
            "Camera",
            "Description",
            new BigDecimal("1000.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("100.00")
        );
    }

    private AuctionCreateRequest invalidAuctionRequest(
        String title,
        String description,
        BigDecimal startingPrice,
        BigDecimal reservePrice,
        BigDecimal minimumBidIncrement
    ) {
        return new AuctionCreateRequest(
            title,
            description,
            null,
            ListingCategory.ELECTRONICS,
            startingPrice,
            reservePrice,
            minimumBidIncrement,
            60L,
            false
        );
    }

    private Auction auction(AuctionStatus auctionStatus, ListingStatus listingStatus) {
        return Auction.builder()
            .listing(Listing.builder()
                .seller(user(SELLER_ID, Role.SELLER))
                .status(listingStatus)
                .build())
            .status(auctionStatus)
            .startingPrice(new BigDecimal("1000.00"))
            .reservePrice(new BigDecimal("1500.00"))
            .minimumBidIncrement(new BigDecimal("100.00"))
            .endsAt(NOW.plusSeconds(60))
            .build();
    }

    private Auction endedAuction(AuctionStatus auctionStatus, ListingStatus listingStatus) {
        Auction auction = auction(auctionStatus, listingStatus);
        auction.setEndsAt(NOW.minusSeconds(60));
        return auction;
    }

    private Bid bid(String amount) {
        return Bid.builder()
            .amount(new BigDecimal(amount))
            .sequenceNumber(1L)
            .build();
    }

    private User user(UUID id, Role role) {
        return User.builder()
            .id(id)
            .email(role.name().toLowerCase() + "@example.test")
            .passwordHash("hash")
            .role(role)
            .createdAt(NOW)
            .build();
    }
}
