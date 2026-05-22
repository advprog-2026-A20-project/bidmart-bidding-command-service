package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingCategory;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import id.ac.ui.cs.advprog.biddingcommand.repository.AuctionRepository;
import id.ac.ui.cs.advprog.biddingcommand.repository.ListingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final AuctionRepository auctionRepository;
    private final Clock clock;
    private final ListingStatusSynchronizer listingStatusSynchronizer;

    public ListingService(
        ListingRepository listingRepository,
        AuctionRepository auctionRepository,
        Clock clock,
        ListingStatusSynchronizer listingStatusSynchronizer
    ) {
        this.listingRepository = listingRepository;
        this.auctionRepository = auctionRepository;
        this.clock = clock;
        this.listingStatusSynchronizer = listingStatusSynchronizer;
    }

    public Listing createAuctionListing(AuctionCreateRequest request, User seller, Instant createdAt) {
        Listing listing = Listing.builder()
            .title(request.title().trim())
            .description(request.description().trim())
            .imageUrl(normalizeImageUrl(request.imageUrl()))
            .price(normalizeMoney(request.startingPrice()))
            .category(resolveCategory(request.category()))
            .seller(seller)
            .createdAt(createdAt)
            .build();
        return listingRepository.save(listing);
    }

    public boolean validateListingForBid(UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));

        if (!isBiddableListingStatus(listing.getStatus())) {
            return false;
        }

        return auctionRepository.findByListingId(listingId)
            .map(auction -> listingStatusSynchronizer.isAuctionBiddable(auction.getStatus()))
            .orElse(false);
    }

    public void updateDisplayedPrice(UUID listingId, BigDecimal displayedPrice) {
        listingRepository.findById(listingId).ifPresent(listing -> {
            listing.setPrice(normalizeMoney(displayedPrice));
            listing.setUpdatedAt(Instant.now(clock));
            listingRepository.save(listing);
        });
    }

    private boolean isBiddableListingStatus(ListingStatus status) {
        return listingStatusSynchronizer.isListingBiddable(status);
    }

    private ListingCategory resolveCategory(ListingCategory category) {
        return category != null ? category : ListingCategory.OTHER;
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return imageUrl.trim();
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
