package id.ac.ui.cs.advprog.biddingcommand.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import id.ac.ui.cs.advprog.biddingcommand.repository.AuctionRepository;
import id.ac.ui.cs.advprog.biddingcommand.repository.BidRepository;

@Service
public class BiddingCommandService {

    private static final Logger log = LoggerFactory.getLogger(BiddingCommandService.class);

    private static final Duration LAST_MINUTE_EXTENSION_WINDOW = Duration.ofMinutes(2);

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ListingService listingService;
    private final WalletClient walletClient;
    private final Clock clock;
    private final Duration closedVisibilityDuration;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionValidator validator;
    private final AuctionResponseMapper responseMapper;
    private final AuctionOutcomeResolver outcomeResolver;
    private final AuctionFactory auctionFactory;
    private final ListingStatusSynchronizer listingStatusSynchronizer;

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
        ListingService listingService,
        WalletClient walletClient,
        Clock clock,
        @Value("${auction.lifecycle.closed-visible-seconds:${AUCTION_CLOSED_VISIBLE_SECONDS:5}}")
        long closedVisibleSeconds,
        ApplicationEventPublisher eventPublisher,
        AuctionValidator validator,
        AuctionResponseMapper responseMapper,
        AuctionOutcomeResolver outcomeResolver,
        AuctionFactory auctionFactory,
        ListingStatusSynchronizer listingStatusSynchronizer
    ) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.listingService = listingService;
        this.walletClient = walletClient;
        this.clock = clock;
        this.closedVisibilityDuration = Duration.ofSeconds(Math.max(0L, closedVisibleSeconds));
        this.eventPublisher = eventPublisher;
        this.validator = validator;
        this.responseMapper = responseMapper;
        this.outcomeResolver = outcomeResolver;
        this.auctionFactory = auctionFactory;
        this.listingStatusSynchronizer = listingStatusSynchronizer;
    }

    @Scheduled(fixedDelayString = "${auction.lifecycle.scan-interval-ms:${AUCTION_LIFECYCLE_SCAN_INTERVAL_MS:1000}}")
    @Transactional
    public void processAuctionLifecycle() {
        Instant now = Instant.now(clock);

        List<Auction> expiredAuctions =
            auctionRepository.findExpiredBiddableAuctionsForUpdate(
                listingStatusSynchronizer.biddableAuctionStatuses(),
                now
            );

        if (!expiredAuctions.isEmpty()) {
            log.info("Closing {} expired auctions at {}", expiredAuctions.size(), now);
        }

        expiredAuctions.forEach(auction -> markAuctionClosed(auction, now));

        Instant resolutionCutoff = now.minus(closedVisibilityDuration);

        List<Auction> closedAuctions =
            auctionRepository.findClosedAuctionsReadyForResolutionForUpdate(
                AuctionStatus.CLOSED,
                resolutionCutoff
            );

        if (!closedAuctions.isEmpty()) {
            log.info("Resolving {} closed auctions at {}", closedAuctions.size(), now);
        }

        closedAuctions.forEach(outcomeResolver::resolveClosedAuction);
    }

    @Transactional(readOnly = false)
    public AuctionDetailResponse getAuctionDetail(UUID auctionId) {
        Auction auction = loadAuctionForUpdate(auctionId);
        closeAuctionIfExpired(auction, Instant.now(clock));
        return toDetailResponse(auction);
    }

    @Transactional
    public AuctionDetailResponse createAuction(AuctionCreateRequest request, UUID sellerId) {
        long durationMinutes = auctionFactory.resolveDurationMinutes(request != null ? request.durationMinutes() : null);
        validator.validateAuctionRequest(request, durationMinutes);

        User seller = validator.loadSeller(sellerId);
        Instant now = Instant.now(clock);

        Auction auction = auctionFactory.buildDraftAuction(
            request,
            listingService.createAuctionListing(request, seller, now),
            now,
            durationMinutes
        );

        if (Boolean.TRUE.equals(request.activateNow())) {
            activateAuctionInternal(auction, now);
        } else {
            listingStatusSynchronizer.syncListingStatus(auction);
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
            new BidPlacedEvent(
                placedBid.getId(),
                context.auction().getId(),
                context.bidder().getId(),
                placedBid.getAmount()
            )
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

        boolean closedBecauseExpired = closeAuctionIfExpired(auction, now);
        if (closedBecauseExpired) {
            validator.ensureAuctionAcceptsBid(auction, bidderId);
        }

        validator.ensureListingAcceptsBid(auction);
        validator.ensureAuctionAcceptsBid(auction, bidderId);

        Bid leadingBid = bidRepository.findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(auctionId)
            .orElse(null);

        BigDecimal bidAmount = auctionFactory.normalizeMoney(request.amount());

        validator.validateBidAmountAgainstMinimum(auction, leadingBid, bidAmount);

        return new BidPlacementContext(auction, bidder, leadingBid, bidAmount);
    }

    private boolean closeAuctionIfExpired(Auction auction, Instant now) {
        if (!validator.shouldCloseAuction(auction, now)) {
            return false;
        }

        markAuctionClosed(auction, now);
        return true;
    }

    private void markAuctionClosed(Auction auction, Instant closedAt) {
        if (!listingStatusSynchronizer.isAuctionBiddable(auction.getStatus())) {
            return;
        }

        auction.setStatus(AuctionStatus.CLOSED);
        auction.setClosedAt(closedAt);

        listingStatusSynchronizer.syncListingStatus(auction);
        auctionRepository.save(auction);
    }

    private void activateAuctionInternal(Auction auction, Instant now) {
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setActivatedAt(now);
        auction.setStartsAt(now);
        auction.setEndsAt(now.plus(Duration.ofMinutes(auction.getDurationMinutes())));

        listingStatusSynchronizer.syncListingStatus(auction);
    }

    private void publishAuctionActivatedEventIfNeeded(Auction auction) {
        if (auction.getStatus() == AuctionStatus.ACTIVE) {
            eventPublisher.publishEvent(
                new AuctionActivatedEvent(
                    auction.getId(),
                    auction.getListing().getId()
                )
            );
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
                listingStatusSynchronizer.syncListingStatus(auction);
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
}
