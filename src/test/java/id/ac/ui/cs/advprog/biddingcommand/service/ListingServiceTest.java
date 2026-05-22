package id.ac.ui.cs.advprog.biddingcommand.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingCategory;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import id.ac.ui.cs.advprog.biddingcommand.repository.AuctionRepository;
import id.ac.ui.cs.advprog.biddingcommand.repository.ListingRepository;
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
class ListingServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private AuctionRepository auctionRepository;

    private ListingService listingService;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(
            listingRepository,
            auctionRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createAuctionListing_shouldTrimTitleAndDescription() {
        AuctionCreateRequest request = requestWith("  Camera  ", "  Good condition  ", ListingCategory.ELECTRONICS, "https://img");
        User seller = seller();
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Listing listing = listingService.createAuctionListing(request, seller, NOW);

        assertEquals("Camera", listing.getTitle());
        assertEquals("Good condition", listing.getDescription());
    }

    @Test
    void createAuctionListing_shouldDefaultCategoryToOtherWhenNull() {
        AuctionCreateRequest request = requestWith("Camera", "Good condition", null, "https://img");
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Listing listing = listingService.createAuctionListing(request, seller(), NOW);

        assertEquals(ListingCategory.OTHER, listing.getCategory());
    }

    @Test
    void createAuctionListing_shouldNormalizeBlankImageUrlToNull() {
        AuctionCreateRequest request = requestWith("Camera", "Good condition", ListingCategory.ELECTRONICS, "   ");
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Listing listing = listingService.createAuctionListing(request, seller(), NOW);

        assertNull(listing.getImageUrl());
    }

    @Test
    void validateListingForBid_shouldThrowNotFoundWhenListingMissing() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> listingService.validateListingForBid(listingId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Listing not found", exception.getReason());
    }

    @Test
    void validateListingForBid_shouldReturnNotBiddableWhenListingInactive() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, ListingStatus.DRAFT)));

        assertFalse(listingService.validateListingForBid(listingId));
    }

    @Test
    void validateListingForBid_shouldReturnNotBiddableWhenAuctionMissing() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, ListingStatus.ACTIVE)));
        when(auctionRepository.findByListingId(listingId)).thenReturn(Optional.empty());

        assertFalse(listingService.validateListingForBid(listingId));
    }

    @Test
    void validateListingForBid_shouldReturnBiddableWhenAuctionActive() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, ListingStatus.ACTIVE)));
        when(auctionRepository.findByListingId(listingId)).thenReturn(Optional.of(auction(AuctionStatus.ACTIVE)));

        assertTrue(listingService.validateListingForBid(listingId));
    }

    @Test
    void validateListingForBid_shouldReturnBiddableWhenAuctionExtended() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, ListingStatus.EXTENDED)));
        when(auctionRepository.findByListingId(listingId)).thenReturn(Optional.of(auction(AuctionStatus.EXTENDED)));

        assertTrue(listingService.validateListingForBid(listingId));
    }

    @Test
    void validateListingForBid_shouldReturnNotBiddableWhenAuctionDraft() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, ListingStatus.ACTIVE)));
        when(auctionRepository.findByListingId(listingId)).thenReturn(Optional.of(auction(AuctionStatus.DRAFT)));

        assertFalse(listingService.validateListingForBid(listingId));
    }

    @Test
    void validateListingForBid_shouldReturnNotBiddableWhenAuctionClosedWonOrUnsold() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing(listingId, ListingStatus.ACTIVE)));
        when(auctionRepository.findByListingId(listingId))
            .thenReturn(Optional.of(auction(AuctionStatus.CLOSED)))
            .thenReturn(Optional.of(auction(AuctionStatus.WON)))
            .thenReturn(Optional.of(auction(AuctionStatus.UNSOLD)));

        assertFalse(listingService.validateListingForBid(listingId));
        assertFalse(listingService.validateListingForBid(listingId));
        assertFalse(listingService.validateListingForBid(listingId));
    }

    @Test
    void updateDisplayedPrice_shouldUpdatePriceAndTimestampWhenListingExists() {
        UUID listingId = UUID.randomUUID();
        Listing listing = listing(listingId, ListingStatus.ACTIVE);
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        listingService.updateDisplayedPrice(listingId, new BigDecimal("1234.50"));

        assertEquals(new BigDecimal("1234.50"), listing.getPrice());
        assertEquals(NOW, listing.getUpdatedAt());
        verify(listingRepository).save(listing);
    }

    @Test
    void updateDisplayedPrice_shouldDoNothingWhenListingNotFound() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        listingService.updateDisplayedPrice(listingId, new BigDecimal("1234.50"));

        verify(listingRepository, never()).save(any(Listing.class));
    }

    private AuctionCreateRequest requestWith(
        String title,
        String description,
        ListingCategory category,
        String imageUrl
    ) {
        return new AuctionCreateRequest(
            title,
            description,
            imageUrl,
            category,
            new BigDecimal("1000.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("100.00"),
            60L,
            false
        );
    }

    private User seller() {
        return User.builder()
            .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .email("seller@bidmart.test")
            .passwordHash("hash")
            .role(Role.SELLER)
            .createdAt(NOW)
            .build();
    }

    private Listing listing(UUID id, ListingStatus status) {
        return Listing.builder()
            .id(id)
            .title("Camera")
            .description("Desc")
            .price(new BigDecimal("1000.00"))
            .category(ListingCategory.ELECTRONICS)
            .seller(seller())
            .status(status)
            .createdAt(NOW)
            .build();
    }

    private Auction auction(AuctionStatus status) {
        return Auction.builder()
            .id(UUID.randomUUID())
            .status(status)
            .build();
    }
}
