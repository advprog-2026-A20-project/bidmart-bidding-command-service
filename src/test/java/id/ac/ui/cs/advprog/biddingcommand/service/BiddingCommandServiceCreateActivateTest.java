package id.ac.ui.cs.advprog.biddingcommand.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BiddingCommandServiceCreateActivateTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:15:30Z");
    private static final UUID SELLER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_SELLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID AUCTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID LISTING_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

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
    void createAuction_shouldCreateDraftAuction() {
        AuctionCreateRequest request = validRequest(false).build();
        User seller = seller(SELLER_ID);
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> {
            Listing listing = invocation.getArgument(0);
            listing.setId(LISTING_ID);
            return listing;
        });
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> {
            Auction auction = invocation.getArgument(0);
            auction.setId(AUCTION_ID);
            return auction;
        });
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of());

        AuctionDetailResponse response = biddingCommandService.createAuction(request, SELLER_ID);

        ArgumentCaptor<Auction> auctionCaptor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(auctionCaptor.capture());
        Auction savedAuction = auctionCaptor.getValue();
        assertEquals(AuctionStatus.DRAFT, savedAuction.getStatus());
        assertEquals(NOW, savedAuction.getCreatedAt());
        assertEquals(60L, savedAuction.getDurationMinutes());
        assertEquals(ListingStatus.DRAFT, savedAuction.getListing().getStatus());
        assertEquals(NOW, savedAuction.getListing().getUpdatedAt());
        assertEquals(AuctionStatus.DRAFT, response.status());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void createAuction_activateNow_shouldActivateAndPublishEvent() {
        AuctionCreateRequest request = validRequest(true).build();
        User seller = seller(SELLER_ID);
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> {
            Listing listing = invocation.getArgument(0);
            listing.setId(LISTING_ID);
            return listing;
        });
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> {
            Auction auction = invocation.getArgument(0);
            auction.setId(AUCTION_ID);
            return auction;
        });
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of());

        AuctionDetailResponse response = biddingCommandService.createAuction(request, SELLER_ID);

        ArgumentCaptor<Auction> auctionCaptor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(auctionCaptor.capture());
        Auction savedAuction = auctionCaptor.getValue();
        assertEquals(AuctionStatus.ACTIVE, savedAuction.getStatus());
        assertEquals(NOW, savedAuction.getActivatedAt());
        assertEquals(NOW, savedAuction.getStartsAt());
        assertEquals(NOW.plusSeconds(60L * 60L), savedAuction.getEndsAt());
        assertEquals(ListingStatus.ACTIVE, savedAuction.getListing().getStatus());
        assertEquals(AuctionStatus.ACTIVE, response.status());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void createAuction_withNullRequest_shouldThrowBadRequest() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.createAuction(null, SELLER_ID)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Auction request is required", exception.getReason());
    }

    @Test
    void createAuction_withBlankTitle_shouldThrowBadRequest() {
        assertBadRequest(validRequest(false).withTitle("   "), "Title is required");
    }

    @Test
    void createAuction_withBlankDescription_shouldThrowBadRequest() {
        assertBadRequest(validRequest(false).withDescription("   "), "Description is required");
    }

    @Test
    void createAuction_withInvalidStartingPrice_shouldThrowBadRequest() {
        assertBadRequest(validRequest(false).withStartingPrice(BigDecimal.ZERO), "Starting price must be positive");
    }

    @Test
    void createAuction_withInvalidReservePrice_shouldThrowBadRequest() {
        assertBadRequest(validRequest(false).withReservePrice(BigDecimal.ZERO), "Reserve price must be positive");
    }

    @Test
    void createAuction_withReservePriceLowerThanStartingPrice_shouldThrowBadRequest() {
        assertBadRequest(
            validRequest(false).withStartingPrice(new BigDecimal("1000.00")).withReservePrice(new BigDecimal("999.99")),
            "Reserve price must be greater than or equal to starting price"
        );
    }

    @Test
    void createAuction_withInvalidMinimumIncrement_shouldThrowBadRequest() {
        assertBadRequest(
            validRequest(false).withMinimumBidIncrement(BigDecimal.ZERO),
            "Minimum bid increment must be positive"
        );
    }

    @Test
    void createAuction_withInvalidDuration_shouldThrowBadRequest() {
        assertBadRequest(validRequest(false).withDurationMinutes(0L), "Duration must be between 1 and 20160 minutes");
    }

    @Test
    void activateAuction_byOwner_shouldActivateAuction() {
        Auction draftAuction = draftAuction(seller(SELLER_ID));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(draftAuction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bidRepository.findByAuctionIdOrderBySequenceNumberAsc(AUCTION_ID)).thenReturn(List.of());

        AuctionDetailResponse response = biddingCommandService.activateAuction(AUCTION_ID, SELLER_ID);

        assertEquals(AuctionStatus.ACTIVE, draftAuction.getStatus());
        assertEquals(NOW, draftAuction.getActivatedAt());
        assertEquals(NOW, draftAuction.getStartsAt());
        assertEquals(NOW.plusSeconds(60L * 60L), draftAuction.getEndsAt());
        assertEquals(ListingStatus.ACTIVE, draftAuction.getListing().getStatus());
        assertEquals(AuctionStatus.ACTIVE, response.status());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void activateAuction_byNonOwner_shouldThrowForbidden() {
        Auction draftAuction = draftAuction(seller(SELLER_ID));
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(draftAuction));
        when(userRepository.findById(OTHER_SELLER_ID)).thenReturn(Optional.of(seller(OTHER_SELLER_ID)));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.activateAuction(AUCTION_ID, OTHER_SELLER_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Only the seller can manage this auction", exception.getReason());
    }

    @Test
    void activateAuction_whenAuctionNotFound_shouldThrowNotFound() {
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.activateAuction(AUCTION_ID, SELLER_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Auction not found", exception.getReason());
    }

    @Test
    void activateAuction_whenNotDraft_shouldThrowConflict() {
        Auction activeAuction = draftAuction(seller(SELLER_ID));
        activeAuction.setStatus(AuctionStatus.ACTIVE);
        when(auctionRepository.findByIdWithListingAndSellerForUpdate(AUCTION_ID)).thenReturn(Optional.of(activeAuction));
        when(userRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller(SELLER_ID)));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.activateAuction(AUCTION_ID, SELLER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Only draft auctions can be activated", exception.getReason());
    }

    private void assertBadRequest(AuctionCreateRequestBuilder builder, String reason) {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> biddingCommandService.createAuction(builder.build(), SELLER_ID)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(reason, exception.getReason());
    }

    private AuctionCreateRequestBuilder validRequest(boolean activateNow) {
        return new AuctionCreateRequestBuilder()
            .title("Vintage Camera")
            .description("Well kept camera")
            .imageUrl("https://example.com/camera.jpg")
            .category(ListingCategory.ELECTRONICS)
            .startingPrice(new BigDecimal("1000.00"))
            .reservePrice(new BigDecimal("1500.00"))
            .minimumBidIncrement(new BigDecimal("100.00"))
            .durationMinutes(60L)
            .activateNow(activateNow);
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

    private Auction draftAuction(User seller) {
        Listing listing = Listing.builder()
            .id(LISTING_ID)
            .title("Vintage Camera")
            .description("Well kept camera")
            .price(new BigDecimal("1000.00"))
            .category(ListingCategory.ELECTRONICS)
            .seller(seller)
            .status(ListingStatus.DRAFT)
            .createdAt(NOW.minusSeconds(120))
            .build();
        return Auction.builder()
            .id(AUCTION_ID)
            .listing(listing)
            .status(AuctionStatus.DRAFT)
            .startingPrice(new BigDecimal("1000.00"))
            .reservePrice(new BigDecimal("1500.00"))
            .minimumBidIncrement(new BigDecimal("100.00"))
            .durationMinutes(60L)
            .nextBidSequence(1L)
            .extensionCount(0)
            .createdAt(NOW.minusSeconds(120))
            .build();
    }

    private static final class AuctionCreateRequestBuilder {
        private String title;
        private String description;
        private String imageUrl;
        private ListingCategory category;
        private BigDecimal startingPrice;
        private BigDecimal reservePrice;
        private BigDecimal minimumBidIncrement;
        private Long durationMinutes;
        private Boolean activateNow;

        AuctionCreateRequestBuilder title(String value) {
            this.title = value;
            return this;
        }

        AuctionCreateRequestBuilder description(String value) {
            this.description = value;
            return this;
        }

        AuctionCreateRequestBuilder imageUrl(String value) {
            this.imageUrl = value;
            return this;
        }

        AuctionCreateRequestBuilder category(ListingCategory value) {
            this.category = value;
            return this;
        }

        AuctionCreateRequestBuilder startingPrice(BigDecimal value) {
            this.startingPrice = value;
            return this;
        }

        AuctionCreateRequestBuilder reservePrice(BigDecimal value) {
            this.reservePrice = value;
            return this;
        }

        AuctionCreateRequestBuilder minimumBidIncrement(BigDecimal value) {
            this.minimumBidIncrement = value;
            return this;
        }

        AuctionCreateRequestBuilder durationMinutes(Long value) {
            this.durationMinutes = value;
            return this;
        }

        AuctionCreateRequestBuilder activateNow(Boolean value) {
            this.activateNow = value;
            return this;
        }

        AuctionCreateRequestBuilder withTitle(String value) {
            return title(value);
        }

        AuctionCreateRequestBuilder withDescription(String value) {
            return description(value);
        }

        AuctionCreateRequestBuilder withStartingPrice(BigDecimal value) {
            return startingPrice(value);
        }

        AuctionCreateRequestBuilder withReservePrice(BigDecimal value) {
            return reservePrice(value);
        }

        AuctionCreateRequestBuilder withMinimumBidIncrement(BigDecimal value) {
            return minimumBidIncrement(value);
        }

        AuctionCreateRequestBuilder withDurationMinutes(Long value) {
            return durationMinutes(value);
        }

        AuctionCreateRequest build() {
            return new AuctionCreateRequest(
                title,
                description,
                imageUrl,
                category,
                startingPrice,
                reservePrice,
                minimumBidIncrement,
                durationMinutes,
                activateNow
            );
        }
    }
}
