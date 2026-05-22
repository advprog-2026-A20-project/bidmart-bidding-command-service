package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import id.ac.ui.cs.advprog.biddingcommand.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class AuctionValidator {

    private final UserRepository userRepository;
    private final Clock clock;
    private final long maxDurationMinutes;

    AuctionValidator(UserRepository userRepository, Clock clock, long maxDurationMinutes) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.maxDurationMinutes = maxDurationMinutes;
    }

    void validateAuctionRequest(AuctionCreateRequest request, long durationMinutes) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auction request is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required");
        }
        if (request.startingPrice() == null || request.startingPrice().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Starting price must be positive");
        }
        if (request.reservePrice() == null || request.reservePrice().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reserve price must be positive");
        }
        if (request.reservePrice().compareTo(request.startingPrice()) < 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Reserve price must be greater than or equal to starting price"
            );
        }
        if (request.minimumBidIncrement() != null && request.minimumBidIncrement().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum bid increment must be positive");
        }
        if (durationMinutes < 1 || durationMinutes > maxDurationMinutes) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Duration must be between 1 and " + maxDurationMinutes + " minutes"
            );
        }
    }

    void validateBidRequest(BidPlaceRequest request) {
        if (request == null || request.amount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bid amount is required");
        }
        if (request.amount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bid amount must be positive");
        }
    }

    User loadBuyer(UUID buyerId) {
        User buyer = userRepository.findById(buyerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (buyer.getRole() != Role.BUYER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only BUYER can place bids");
        }
        return buyer;
    }

    User loadSeller(UUID sellerId) {
        User seller = userRepository.findById(sellerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (seller.getRole() != Role.SELLER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only SELLER can manage auctions");
        }
        return seller;
    }

    void ensureSellerOwnsAuction(Auction auction, UUID sellerId) {
        loadSeller(sellerId);
        if (!Objects.equals(auction.getListing().getSeller().getId(), sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the seller can manage this auction");
        }
    }

    void ensureListingAcceptsBid(Auction auction) {
        if (!isBiddableListingStatus(auction.getListing().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Listing is not accepting bids");
        }
    }

    void ensureAuctionAcceptsBid(Auction auction, UUID bidderId) {
        if (!isBiddableStatus(auction.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction is not accepting bids");
        }
        if (auction.getEndsAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction has no valid end time");
        }
        if (auction.getEndsAt().isBefore(Instant.now(clock))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction has already ended");
        }
        if (Objects.equals(auction.getListing().getSeller().getId(), bidderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seller cannot bid on their own auction");
        }
    }

    void validateBidAmountAgainstMinimum(Auction auction, Bid leadingBid, BigDecimal bidAmount) {
        BigDecimal nextMinimumBid = calculateNextMinimumBid(auction, leadingBid);
        if (bidAmount.compareTo(nextMinimumBid) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bid must be at least " + nextMinimumBid);
        }
    }

    void validateManualClosureAllowed(Auction auction, Instant now) {
        if (!isBiddableStatus(auction.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction is already closed");
        }
        if (auction.getEndsAt() != null && now.isBefore(auction.getEndsAt())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Auction cannot be closed before its scheduled end"
            );
        }
    }

    boolean shouldCloseAuction(Auction auction, Instant now) {
        return isBiddableStatus(auction.getStatus())
            && auction.getEndsAt() != null
            && !auction.getEndsAt().isAfter(now);
    }

    BigDecimal calculateNextMinimumBid(Auction auction, Bid leadingBid) {
        if (leadingBid == null) {
            return auction.getStartingPrice();
        }
        return leadingBid.getAmount().add(auction.getMinimumBidIncrement());
    }

    boolean isBiddableStatus(AuctionStatus status) {
        return status == AuctionStatus.ACTIVE || status == AuctionStatus.EXTENDED;
    }

    private boolean isBiddableListingStatus(ListingStatus status) {
        return status == ListingStatus.ACTIVE || status == ListingStatus.EXTENDED;
    }
}
