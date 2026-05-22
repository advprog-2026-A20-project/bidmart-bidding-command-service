package id.ac.ui.cs.advprog.biddingcommand.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListingStatusSynchronizerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private ListingStatusSynchronizer listingStatusSynchronizer;

    @BeforeEach
    void setUp() {
        listingStatusSynchronizer = new ListingStatusSynchronizer(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void toListingStatusShouldMapEveryAuctionStatus() {
        Map<AuctionStatus, ListingStatus> expectedMappings = Map.of(
            AuctionStatus.DRAFT, ListingStatus.DRAFT,
            AuctionStatus.ACTIVE, ListingStatus.ACTIVE,
            AuctionStatus.EXTENDED, ListingStatus.EXTENDED,
            AuctionStatus.CLOSED, ListingStatus.CLOSED,
            AuctionStatus.WON, ListingStatus.WON,
            AuctionStatus.UNSOLD, ListingStatus.UNSOLD,
            AuctionStatus.CANCELLED, ListingStatus.CANCELLED
        );

        expectedMappings.forEach((auctionStatus, listingStatus) ->
            assertEquals(listingStatus, listingStatusSynchronizer.toListingStatus(auctionStatus))
        );
    }

    @Test
    void syncListingStatusShouldUpdateListingStatusAndTimestampWhenStatusChanges() {
        Listing listing = Listing.builder()
            .status(ListingStatus.DRAFT)
            .build();
        Auction auction = Auction.builder()
            .status(AuctionStatus.ACTIVE)
            .listing(listing)
            .build();

        listingStatusSynchronizer.syncListingStatus(auction);

        assertEquals(ListingStatus.ACTIVE, listing.getStatus());
        assertEquals(NOW, listing.getUpdatedAt());
    }

    @Test
    void syncListingStatusShouldNotChangeTimestampWhenStatusAlreadyMatches() {
        Instant originalUpdatedAt = NOW.minusSeconds(30);
        Listing listing = Listing.builder()
            .status(ListingStatus.ACTIVE)
            .updatedAt(originalUpdatedAt)
            .build();
        Auction auction = Auction.builder()
            .status(AuctionStatus.ACTIVE)
            .listing(listing)
            .build();

        listingStatusSynchronizer.syncListingStatus(auction);

        assertEquals(ListingStatus.ACTIVE, listing.getStatus());
        assertEquals(originalUpdatedAt, listing.getUpdatedAt());
    }

    @Test
    void syncListingStatusShouldIgnoreNullAuctionData() {
        assertDoesNotThrow(() -> listingStatusSynchronizer.syncListingStatus(null));
        assertDoesNotThrow(() -> listingStatusSynchronizer.syncListingStatus(Auction.builder().build()));

        assertNull(Auction.builder().build().getListing());
    }
}
