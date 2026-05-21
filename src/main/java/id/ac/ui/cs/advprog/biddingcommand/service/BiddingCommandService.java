package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BiddingCommandService {

    private static final BigDecimal DEFAULT_MINIMUM_INCREMENT = money("1.00");
    private static final long DEFAULT_DURATION_MINUTES = 60L;
    private static final long MAX_DURATION_MINUTES = 14L * 24L * 60L;
    private static final Duration LAST_MINUTE_EXTENSION_WINDOW = Duration.ofMinutes(2);
    private static final Comparator<Bid> LEADING_BID_COMPARATOR = Comparator
        .comparing(Bid::getAmount)
        .thenComparing(Bid::getSequenceNumber, Comparator.reverseOrder());

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final WalletClient walletClient;
    private final Clock clock;

    private record BidPlacementContext(
        Auction auction,
        User bidder,
        Bid leadingBidBeforePlacement,
        BigDecimal bidAmount
    ) {
    }

    public BiddingCommandService(
        AuctionRepository auctionRepository,
        BidRepository bidRepository,
        ListingRepository listingRepository,
        UserRepository userRepository,
        WalletClient walletClient,
        Clock clock
    ) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.walletClient = walletClient;
        this.clock = clock;
    }

    @Transactional
    public AuctionDetailResponse createAuction(AuctionCreateRequest request, UUID sellerId) {
        validateAuctionRequest(request);
        User seller = loadSeller(sellerId);
        Instant now = Instant.now(clock);

        Listing listing = createAuctionListing(request, seller, now);
        Auction auction = buildDraftAuction(request, listing, now);
        if (Boolean.TRUE.equals(request.activateNow())) {
            activateAuctionInternal(auction, now);
        } else {
            syncListingStatus(auction);
        }

        return toDetailResponse(auctionRepository.save(auction));
    }

    @Transactional
    public AuctionDetailResponse activateAuction(UUID auctionId, UUID sellerId) {
        Auction auction = loadAuctionForUpdate(auctionId);
        ensureSellerOwnsAuction(auction, sellerId);
        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft auctions can be activated");
        }

        activateAuctionInternal(auction, Instant.now(clock));
        return toDetailResponse(auctionRepository.save(auction));
    }

    @Transactional
    public AuctionDetailResponse placeBid(UUID auctionId, BidPlaceRequest request, UUID bidderId) {
        Instant now = Instant.now(clock);
        BidPlacementContext context = prepareBidPlacement(auctionId, request, bidderId, now);
        holdBidderFunds(context);
        Bid placedBid = persistBid(context, now);
        updateAuctionAfterBid(context.auction(), now);
        releasePreviousLeaderFundsIfNeeded(context);
        return toDetailResponse(context.auction());
    }

    @Transactional
    public AuctionDetailResponse closeAuction(UUID auctionId, UUID sellerId) {
        Auction auction = loadAuctionForUpdate(auctionId);
        ensureSellerOwnsAuction(auction, sellerId);
        Instant now = Instant.now(clock);
        validateManualClosureAllowed(auction, now);
        closeAuctionInternal(auction, now);
        return toDetailResponse(auction);
    }

    private BidPlacementContext prepareBidPlacement(
        UUID auctionId,
        BidPlaceRequest request,
        UUID bidderId,
        Instant now
    ) {
        validateBidRequest(request);
        User bidder = loadBuyer(bidderId);
        Auction auction = loadAuctionForUpdate(auctionId);
        closeAuctionIfExpired(auction, now);
        ensureAuctionAcceptsBid(auction, bidderId);

        Bid leadingBid = bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(auctionId).orElse(null);
        BigDecimal bidAmount = normalizeMoney(request.amount());
        validateBidAmountAgainstMinimum(auction, leadingBid, bidAmount);
        return new BidPlacementContext(auction, bidder, leadingBid, bidAmount);
    }

    private void closeAuctionIfExpired(Auction auction, Instant now) {
        if (!shouldCloseAuction(auction, now)) {
            syncListingStatus(auction);
            return;
        }
        closeAuctionInternal(auction, now);
    }

    private boolean shouldCloseAuction(Auction auction, Instant now) {
        return isBiddableStatus(auction.getStatus())
            && auction.getEndsAt() != null
            && !auction.getEndsAt().isAfter(now);
    }

    private void closeAuctionInternal(Auction auction, Instant closedAt) {
        if (!isBiddableStatus(auction.getStatus())) {
            return;
        }

        Bid leadingBid = bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(auction.getId())
            .orElse(null);
        auction.setStatus(AuctionStatus.CLOSED);
        auction.setClosedAt(closedAt);

        boolean reserveMet = leadingBid != null && leadingBid.getAmount().compareTo(auction.getReservePrice()) >= 0;
        if (reserveMet) {
            walletClient.captureFunds(leadingBid.getBidder().getId(), auction.getId(), leadingBid.getAmount());
            walletClient.creditFunds(
                auction.getListing().getSeller().getId(),
                auction.getId(),
                leadingBid.getAmount()
            );
            auction.setStatus(AuctionStatus.WON);
        } else {
            if (leadingBid != null) {
                walletClient.releaseFunds(leadingBid.getBidder().getId(), auction.getId(), leadingBid.getAmount());
            }
            auction.setStatus(AuctionStatus.UNSOLD);
        }
        syncListingStatus(auction);
        auctionRepository.save(auction);
    }

    private void ensureAuctionAcceptsBid(Auction auction, UUID bidderId) {
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

    private boolean isBiddableStatus(AuctionStatus status) {
        return status == AuctionStatus.ACTIVE || status == AuctionStatus.EXTENDED;
    }

    private Listing createAuctionListing(AuctionCreateRequest request, User seller, Instant createdAt) {
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

    private Auction buildDraftAuction(AuctionCreateRequest request, Listing listing, Instant createdAt) {
        return Auction.builder()
            .listing(listing)
            .status(AuctionStatus.DRAFT)
            .startingPrice(normalizeMoney(request.startingPrice()))
            .reservePrice(normalizeMoney(request.reservePrice()))
            .minimumBidIncrement(normalizeMoney(defaultIncrement(request.minimumBidIncrement())))
            .durationMinutes(defaultDuration(request.durationMinutes()))
            .createdAt(createdAt)
            .nextBidSequence(1L)
            .extensionCount(0)
            .build();
    }

    private void activateAuctionInternal(Auction auction, Instant now) {
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setActivatedAt(now);
        auction.setStartsAt(now);
        auction.setEndsAt(now.plus(Duration.ofMinutes(auction.getDurationMinutes())));
        syncListingStatus(auction);
    }

    private void validateBidAmountAgainstMinimum(Auction auction, Bid leadingBid, BigDecimal bidAmount) {
        BigDecimal nextMinimumBid = calculateNextMinimumBid(auction, leadingBid);
        if (bidAmount.compareTo(nextMinimumBid) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bid must be at least " + nextMinimumBid);
        }
    }

    private BigDecimal calculateNextMinimumBid(Auction auction, Bid leadingBid) {
        if (leadingBid == null) {
            return auction.getStartingPrice();
        }
        return normalizeMoney(leadingBid.getAmount().add(auction.getMinimumBidIncrement()));
    }

    private void holdBidderFunds(BidPlacementContext context) {
        walletClient.holdFunds(
            context.bidder().getId(),
            context.auction().getId(),
            calculateRequiredHold(context)
        );
    }

    private BigDecimal calculateRequiredHold(BidPlacementContext context) {
        Bid leadingBid = context.leadingBidBeforePlacement();
        if (leadingBid != null && Objects.equals(leadingBid.getBidder().getId(), context.bidder().getId())) {
            return context.bidAmount().subtract(leadingBid.getAmount());
        }
        return context.bidAmount();
    }

    private Bid persistBid(BidPlacementContext context, Instant now) {
        Auction auction = context.auction();
        Bid bid = Bid.builder()
            .auction(auction)
            .bidder(context.bidder())
            .amount(context.bidAmount())
            .sequenceNumber(auction.getNextBidSequence())
            .submittedAt(now)
            .build();
        auction.setNextBidSequence(auction.getNextBidSequence() + 1);
        return bidRepository.save(bid);
    }

    private void updateAuctionAfterBid(Auction auction, Instant bidReceivedAt) {
        extendAuctionIfNeeded(auction, bidReceivedAt);
        auctionRepository.save(auction);
    }

    private void extendAuctionIfNeeded(Auction auction, Instant bidReceivedAt) {
        if (auction.getEndsAt() == null) {
            return;
        }
        Instant extensionThreshold = auction.getEndsAt().minus(LAST_MINUTE_EXTENSION_WINDOW);
        if (!bidReceivedAt.isBefore(extensionThreshold)) {
            auction.setEndsAt(bidReceivedAt.plus(LAST_MINUTE_EXTENSION_WINDOW));
            auction.setExtensionCount(auction.getExtensionCount() + 1);
            if (auction.getStatus() == AuctionStatus.ACTIVE) {
                auction.setStatus(AuctionStatus.EXTENDED);
                syncListingStatus(auction);
            }
        }
    }

    private void releasePreviousLeaderFundsIfNeeded(BidPlacementContext context) {
        Bid previousLeader = context.leadingBidBeforePlacement();
        if (previousLeader == null) {
            return;
        }
        if (Objects.equals(previousLeader.getBidder().getId(), context.bidder().getId())) {
            return;
        }
        walletClient.releaseFunds(
            previousLeader.getBidder().getId(),
            context.auction().getId(),
            previousLeader.getAmount()
        );
    }

    private void validateManualClosureAllowed(Auction auction, Instant now) {
        if (!isBiddableStatus(auction.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction is already closed");
        }
        if (bidRepository.countByAuctionId(auction.getId()) > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Auction with bids is closed automatically by the system"
            );
        }
        if (auction.getEndsAt() != null && now.isBefore(auction.getEndsAt())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Auction cannot be closed before its scheduled end"
            );
        }
    }

    private void syncListingStatus(Auction auction) {
        if (auction == null || auction.getListing() == null || auction.getStatus() == null) {
            return;
        }
        ListingStatus listingStatus = switch (auction.getStatus()) {
            case DRAFT -> ListingStatus.DRAFT;
            case ACTIVE -> ListingStatus.ACTIVE;
            case EXTENDED -> ListingStatus.EXTENDED;
            case CLOSED -> ListingStatus.CLOSED;
            case WON -> ListingStatus.WON;
            case UNSOLD -> ListingStatus.UNSOLD;
            case CANCELLED -> ListingStatus.CANCELLED;
        };
        if (auction.getListing().getStatus() != listingStatus) {
            auction.getListing().setStatus(listingStatus);
            auction.getListing().setUpdatedAt(Instant.now(clock));
        }
    }

    private AuctionDetailResponse toDetailResponse(Auction auction) {
        List<Bid> bids = bidRepository.findByAuctionIdOrderBySequenceNumberAsc(auction.getId());
        Bid leadingBid = selectLeadingBid(bids);
        boolean reserveMet = leadingBid != null && leadingBid.getAmount().compareTo(auction.getReservePrice()) >= 0;
        Bid winningBid = auction.getStatus() == AuctionStatus.WON ? leadingBid : null;
        BigDecimal currentPrice = leadingBid == null ? auction.getListing().getPrice() : leadingBid.getAmount();
        return new AuctionDetailResponse(
            auction.getId(),
            auction.getListing().getId(),
            auction.getListing().getTitle(),
            auction.getListing().getDescription(),
            auction.getListing().getSeller().getId(),
            auction.getListing().getSeller().getEmail(),
            currentPrice,
            auction.getStartingPrice(),
            auction.getReservePrice(),
            auction.getMinimumBidIncrement(),
            auction.getStatus(),
            auction.getCreatedAt(),
            auction.getStartsAt(),
            auction.getEndsAt(),
            auction.getClosedAt(),
            auction.getDurationMinutes(),
            auction.getExtensionCount(),
            bids.size(),
            calculateNextMinimumBid(auction, leadingBid),
            reserveMet,
            isBiddableStatus(auction.getStatus()),
            leadingBid == null ? null : toBidResponse(auction, leadingBid, leadingBid),
            winningBid == null ? null : toBidResponse(auction, winningBid, leadingBid),
            bids.stream().map(bid -> toBidResponse(auction, bid, leadingBid)).toList()
        );
    }

    private BidResponse toBidResponse(Auction auction, Bid bid, Bid leadingBid) {
        boolean isWinningBid = leadingBid != null
            && Objects.equals(leadingBid.getId(), bid.getId())
            && auction.getStatus() != AuctionStatus.UNSOLD;
        return new BidResponse(
            bid.getId(),
            bid.getBidder().getId(),
            bid.getBidder().getEmail(),
            bid.getAmount(),
            bid.getSequenceNumber(),
            bid.getSubmittedAt(),
            isWinningBid
        );
    }

    private Bid selectLeadingBid(List<Bid> bids) {
        return bids.stream().max(LEADING_BID_COMPARATOR).orElse(null);
    }

    private Auction loadAuctionForUpdate(UUID auctionId) {
        return auctionRepository.findByIdWithListingAndSellerForUpdate(auctionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));
    }

    private User loadBuyer(UUID buyerId) {
        User buyer = userRepository.findById(buyerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (buyer.getRole() != Role.BUYER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only BUYER can place bids");
        }
        return buyer;
    }

    private User loadSeller(UUID sellerId) {
        User seller = userRepository.findById(sellerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (seller.getRole() != Role.SELLER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only SELLER can manage auctions");
        }
        return seller;
    }

    private void ensureSellerOwnsAuction(Auction auction, UUID sellerId) {
        loadSeller(sellerId);
        if (!Objects.equals(auction.getListing().getSeller().getId(), sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the seller can manage this auction");
        }
    }

    private void validateAuctionRequest(AuctionCreateRequest request) {
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
        long durationMinutes = defaultDuration(request.durationMinutes());
        if (durationMinutes < 1 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Duration must be between 1 and " + MAX_DURATION_MINUTES + " minutes"
            );
        }
    }

    private void validateBidRequest(BidPlaceRequest request) {
        if (request == null || request.amount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bid amount is required");
        }
        if (request.amount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bid amount must be positive");
        }
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private ListingCategory resolveCategory(ListingCategory category) {
        return category != null ? category : ListingCategory.OTHER;
    }

    private BigDecimal defaultIncrement(BigDecimal increment) {
        return increment == null ? DEFAULT_MINIMUM_INCREMENT : increment;
    }

    private long defaultDuration(Long durationMinutes) {
        return durationMinutes == null ? DEFAULT_DURATION_MINUTES : durationMinutes;
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return imageUrl.trim();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}
