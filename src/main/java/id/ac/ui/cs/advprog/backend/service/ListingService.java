package id.ac.ui.cs.advprog.backend.service;

import id.ac.ui.cs.advprog.backend.dto.ListingBidValidationResponse;
import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.Listing;
import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import id.ac.ui.cs.advprog.backend.model.ListingStatus;
import id.ac.ui.cs.advprog.backend.model.User;
import id.ac.ui.cs.advprog.backend.repository.AuctionRepository;
import id.ac.ui.cs.advprog.backend.repository.ListingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final AuctionRepository auctionRepository;

    public ListingService(ListingRepository listingRepository, AuctionRepository auctionRepository) {
        this.listingRepository = listingRepository;
        this.auctionRepository = auctionRepository;
    }

    @Transactional
    public Listing createAuctionListing(
        String title,
        String description,
        String imageUrl,
        BigDecimal startingPrice,
        ListingCategory category,
        User seller,
        Instant createdAt
    ) {
        Listing listing = Listing.builder()
            .title(title.trim())
            .description(description.trim())
            .imageUrl(normalizeImageUrl(imageUrl))
            .price(startingPrice)
            .category(category == null ? ListingCategory.OTHER : category)
            .seller(seller)
            .status(ListingStatus.ACTIVE)
            .createdAt(createdAt)
            .build();
        return listingRepository.save(listing);
    }

    @Transactional(readOnly = true)
    public ListingBidValidationResponse validateListingForBid(UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        Optional<Auction> optionalAuction = auctionRepository.findByListingId(listingId);
        Auction auction = optionalAuction.orElse(null);

        boolean active = listing.getStatus() == ListingStatus.ACTIVE;
        if (!active) {
            return new ListingBidValidationResponse(
                listing.getId(),
                false,
                false,
                "Listing is no longer active",
                listing.getStatus(),
                auction == null ? null : auction.getStatus(),
                auction == null ? null : auction.getEndsAt()
            );
        }

        if (auction == null) {
            return new ListingBidValidationResponse(
                listing.getId(),
                true,
                false,
                "Listing is not attached to an auction",
                listing.getStatus(),
                null,
                null
            );
        }

        boolean biddable = auction.getStatus() == AuctionStatus.ACTIVE || auction.getStatus() == AuctionStatus.EXTENDED;
        return new ListingBidValidationResponse(
            listing.getId(),
            true,
            biddable,
            biddable ? "Listing is valid for bidding" : "Auction is not accepting bids",
            listing.getStatus(),
            auction.getStatus(),
            auction.getEndsAt()
        );
    }

    @Transactional
    public void updateDisplayedPrice(UUID listingId, BigDecimal latestPrice) {
        listingRepository.findById(listingId).ifPresent(listing -> {
            listing.setPrice(latestPrice);
            listing.setUpdatedAt(Instant.now());
            listingRepository.save(listing);
        });
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return imageUrl.trim();
    }
}
