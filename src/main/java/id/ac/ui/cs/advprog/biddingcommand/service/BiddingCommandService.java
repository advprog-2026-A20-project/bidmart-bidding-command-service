package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingCategory;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BiddingCommandService {

    private static final BigDecimal DEFAULT_MINIMUM_INCREMENT = money("1.00");
    private static final long DEFAULT_DURATION_MINUTES = 60L;
    private static final long MAX_DURATION_MINUTES = 14L * 24L * 60L;
    private static final Duration LAST_MINUTE_EXTENSION_WINDOW = Duration.ofMinutes(2);
    private static final List<AuctionStatus> BIDDABLE_STATUSES = List.of(AuctionStatus.ACTIVE, AuctionStatus.EXTENDED);

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ListingRepository listingRepository;
    private final WalletClient walletClient;
    private final Clock clock;
    private final Duration closedVisibilityDuration;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionValidator validator;
    private final AuctionResponseMapper responseMapper;
    private final AuctionOutcomeResolver outcomeResolver;

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
        Clock clock,
        @Value("${auction.lifecycle.closed-visible-seconds:${AUCTION_CLOSED_VISIBLE_SECONDS:5}}")
        long closedVisibleSeconds,
        ApplicationEventPublisher eventPublisher
    ) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.listingRepository = listingRepository;
        this.walletClient = walletClient;
        this.clock = clock;
        this.closedVisibilityDuration = Duration.ofSeconds(Math.max(0L, closedVisibleSeconds));
        this.eventPublisher = eventPublisher;
        this.validator = new AuctionValidator(userRepository, clock, MAX_DURATION_MINUTES);
        this.responseMapper = new AuctionResponseMapper();
        this.outcomeResolver = new AuctionOutcomeResolver(
            walletClient,
            auctionRepository,
            bidRepository,
            eventPublisher,
            clock
        );
    }

    @Scheduled(fixedDelayString = "${auction.lifecycle.scan-interval-ms:${AUCTION_LIFECYCLE_SCAN_INTERVAL_MS:1000}}")
    @Transactional
    public void processAuctionLifecycle() {
        Instant now = Instant.now(clock);
        auctionRepository.findExpiredBiddableAuctionsForUpdate(BIDDABLE_STATUSES, now)
            .forEach(auction -> markAuctionClosed(auction, now));

        Instant resolutionCutoff = now.minus(closedVisibilityDuration);
        auctionRepository.findClosedAuctionsReadyForResolutionForUpdate(AuctionStatus.CLOSED, resolutionCutoff)
            .forEach(outcomeResolver::resolveClosedAuction);
    }

    @Transactional
    public AuctionDetailResponse createAuction(AuctionCreateRequest request, UUID sellerId) {
        validator.validateAuctionRequest(request, defaultDuration(request != null ? request.durationMinutes() : null));
        User seller = validator.loadSeller(sellerId);
        Instant now = Instant.now(clock);

        Listing listing = createAuctionListing(request, seller, now);
        Auction auction = buildDraftAuction(request, listing, now);
        if (Boolean.TRUE.equals(request.activateNow())) {
            activateAuctionInternal(auction, now);
        } else {
            syncListingStatus(auction);
        }

        Auction savedAuction = auctionRepository.save(auction);
        publishAuctionActivatedEventIfNeeded(savedAuction);
        return toDetailResponse(savedAuction);
    }

    @Transactional
    public AuctionDetailResponse activateAuction(UUID auctionId, UUID sellerId) {
        Auction auction = loadAuctionForUpdate(auctionId);
        validator.ensureSellerOwnsAuction(auction, sellerId);
        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft auctions can be activated");
        }

        activateAuctionInternal(auction, Instant.now(clock));
        Auction savedAuction = auctionRepository.save(auction);
        publishAuctionActivatedEventIfNeeded(savedAuction);
        return toDetailResponse(savedAuction);
    }

    @Transactional
    public AuctionDetailResponse placeBid(UUID auctionId, BidPlaceRequest request, UUID bidderId) {
        Instant now = Instant.now(clock);
        BidPlacementContext context = prepareBidPlacement(auctionId, request, bidderId, now);
        holdBidderFunds(context);
        Bid placedBid = persistBid(context, now);
        updateAuctionAfterBid(context.auction(), now, context.bidAmount());
        releasePreviousLeaderFundsIfNeeded(context);
        eventPublisher.publishEvent(
            new BidPlacedEvent(placedBid.getId(), context.auction().getId(), context.bidder().getId(), placedBid.getAmount())
        );
        return toDetailResponse(context.auction());
    }

    @Transactional
    public AuctionDetailResponse closeAuction(UUID auctionId, UUID sellerId) {
        Auction auction = loadAuctionForUpdate(auctionId);
        validator.ensureSellerOwnsAuction(auction, sellerId);
        Instant now = Instant.now(clock);
        validator.validateManualClosureAllowed(auction, now);
        markAuctionClosed(auction, now);
        outcomeResolver.resolveClosedAuction(auction);
        return toDetailResponse(auction);
    }

    private BidPlacementContext prepareBidPlacement(
        UUID auctionId,
        BidPlaceRequest request,
        UUID bidderId,
        Instant now
    ) {
        validator.validateBidRequest(request);
        User bidder = validator.loadBuyer(bidderId);
        Auction auction = loadAuctionForUpdate(auctionId);
        validator.ensureListingAcceptsBid(auction);
        closeAuctionIfExpired(auction, now);
        validator.ensureAuctionAcceptsBid(auction, bidderId);

        Bid leadingBid = bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(auctionId).orElse(null);
        BigDecimal bidAmount = normalizeMoney(request.amount());
        validator.validateBidAmountAgainstMinimum(auction, leadingBid, bidAmount);
        return new BidPlacementContext(auction, bidder, leadingBid, bidAmount);
    }

    private void closeAuctionIfExpired(Auction auction, Instant now) {
        if (!shouldCloseAuction(auction, now)) {
            syncListingStatus(auction);
            return;
        }
        markAuctionClosed(auction, now);
    }

    private boolean shouldCloseAuction(Auction auction, Instant now) {
        return validator.shouldCloseAuction(auction, now);
    }

    private void markAuctionClosed(Auction auction, Instant closedAt) {
        if (!validator.isBiddableStatus(auction.getStatus())) {
            return;
        }
        auction.setStatus(AuctionStatus.CLOSED);
        auction.setClosedAt(closedAt);
        syncListingStatus(auction);
        auctionRepository.save(auction);
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

    private void publishAuctionActivatedEventIfNeeded(Auction auction) {
        if (auction.getStatus() == AuctionStatus.ACTIVE) {
            eventPublisher.publishEvent(new AuctionActivatedEvent(auction.getId(), auction.getListing().getId()));
        }
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

    private void updateAuctionAfterBid(Auction auction, Instant bidReceivedAt, BigDecimal bidAmount) {
        auction.getListing().setPrice(bidAmount);
        auction.getListing().setUpdatedAt(bidReceivedAt);
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
        return responseMapper.toDetailResponse(
            auction,
            bidRepository.findByAuctionIdOrderBySequenceNumberAsc(auction.getId())
        );
    }

    private Auction loadAuctionForUpdate(UUID auctionId) {
        return auctionRepository.findByIdWithListingAndSellerForUpdate(auctionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));
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
