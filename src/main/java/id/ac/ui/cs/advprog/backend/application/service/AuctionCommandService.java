package id.ac.ui.cs.advprog.backend.application.service;

import id.ac.ui.cs.advprog.backend.application.port.WalletClient;
import id.ac.ui.cs.advprog.backend.dto.AuctionCommandResponse;
import id.ac.ui.cs.advprog.backend.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.BidCommandResponse;
import id.ac.ui.cs.advprog.backend.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import id.ac.ui.cs.advprog.backend.model.Bid;
import id.ac.ui.cs.advprog.backend.model.BidStatus;
import id.ac.ui.cs.advprog.backend.repository.AuctionRepository;
import id.ac.ui.cs.advprog.backend.repository.BidRepository;
import id.ac.ui.cs.advprog.backend.service.AuctionEventPublisher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuctionCommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionCommandService.class);
    private static final long DEFAULT_DURATION_MINUTES = 60L;

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final WalletClient walletClient;
    private final AuctionEventPublisher auctionEventPublisher;
    private final Clock clock;

    public AuctionCommandService(
        AuctionRepository auctionRepository,
        BidRepository bidRepository,
        WalletClient walletClient,
        AuctionEventPublisher auctionEventPublisher,
        Clock clock
    ) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.walletClient = walletClient;
        this.auctionEventPublisher = auctionEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public AuctionCommandResponse createAuction(AuctionCreateRequest request, UUID sellerId) {
        validateCreateRequest(request);
        if (auctionRepository.existsByListingId(request.listingId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction already exists for listing");
        }

        Instant now = Instant.now(clock);
        Auction auction = Auction.builder()
            .sellerId(sellerId)
            .listingId(request.listingId())
            .status(Boolean.TRUE.equals(request.activateNow()) ? AuctionStatus.ACTIVE : AuctionStatus.DRAFT)
            .startingPrice(normalizeMoney(request.startingPrice()))
            .endsAt(Boolean.TRUE.equals(request.activateNow())
                ? now.plus(Duration.ofMinutes(defaultDuration(request.durationMinutes())))
                : null)
            .createdAt(now)
            .updatedAt(now)
            .build();

        Auction savedAuction = auctionRepository.save(auction);
        if (savedAuction.getStatus() == AuctionStatus.ACTIVE) {
            publishSafely(() -> auctionEventPublisher.publishAuctionActivated(savedAuction));
        }
        return toAuctionResponse(savedAuction);
    }

    @Transactional
    public AuctionCommandResponse activateAuction(UUID auctionId, UUID sellerId) {
        Auction auction = loadAuction(auctionId);
        ensureSellerOwnsAuction(auction, sellerId);
        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft auctions can be activated");
        }

        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndsAt(Instant.now(clock).plus(Duration.ofMinutes(DEFAULT_DURATION_MINUTES)));
        Auction savedAuction = auctionRepository.save(auction);
        publishSafely(() -> auctionEventPublisher.publishAuctionActivated(savedAuction));
        return toAuctionResponse(savedAuction);
    }

    @Transactional
    public BidCommandResponse placeBid(UUID auctionId, BidPlaceRequest request, UUID bidderId) {
        validateBidRequest(request);
        Auction auction = loadAuction(auctionId);
        ensureAuctionAcceptsBid(auction, bidderId);

        BigDecimal amount = normalizeMoney(request.amount());
        ensureBidAmountIsHighEnough(auction, amount);

        walletClient.hold(bidderId, auction.getId(), amount);

        Bid previousWinningBid = bidRepository.findFirstByAuctionIdAndStatus(auction.getId(), BidStatus.WINNING)
            .orElse(null);
        if (previousWinningBid != null) {
            previousWinningBid.setStatus(BidStatus.OUTBID);
            bidRepository.save(previousWinningBid);
            walletClient.release(previousWinningBid.getBidderId(), auction.getId(), previousWinningBid.getAmount());
        }

        Bid bid = Bid.builder()
            .auctionId(auction.getId())
            .bidderId(bidderId)
            .amount(amount)
            .status(BidStatus.WINNING)
            .createdAt(Instant.now(clock))
            .build();
        Bid savedBid = bidRepository.save(bid);

        auction.setCurrentHighestBid(amount);
        auction.setCurrentHighestBidderId(bidderId);
        auctionRepository.save(auction);

        publishSafely(() -> auctionEventPublisher.publishBidPlaced(auction, savedBid, previousWinningBid));
        return toBidResponse(auction, savedBid);
    }

    @Transactional
    public AuctionCommandResponse closeAuction(UUID auctionId, UUID sellerId) {
        Auction auction = loadAuction(auctionId);
        ensureSellerOwnsAuction(auction, sellerId);
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active auctions can be closed");
        }

        auction.setStatus(AuctionStatus.CLOSED);
        Bid winningBid = bidRepository.findFirstByAuctionIdAndStatus(auction.getId(), BidStatus.WINNING).orElse(null);
        if (winningBid != null) {
            walletClient.capture(winningBid.getBidderId(), auction.getId(), winningBid.getAmount());
        }
        Auction savedAuction = auctionRepository.save(auction);
        publishSafely(() -> auctionEventPublisher.publishAuctionResolved(savedAuction, winningBid, winningBid != null));
        return toAuctionResponse(savedAuction);
    }

    private Auction loadAuction(UUID auctionId) {
        return auctionRepository.findById(auctionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));
    }

    private void ensureSellerOwnsAuction(Auction auction, UUID sellerId) {
        if (!Objects.equals(auction.getSellerId(), sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the seller can manage this auction");
        }
    }

    private void ensureAuctionAcceptsBid(Auction auction, UUID bidderId) {
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction is not accepting bids");
        }
        if (Objects.equals(auction.getSellerId(), bidderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seller cannot bid on their own auction");
        }
    }

    private void ensureBidAmountIsHighEnough(Auction auction, BigDecimal amount) {
        if (auction.getCurrentHighestBid() == null) {
            if (amount.compareTo(auction.getStartingPrice()) < 0) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bid must be at least " + auction.getStartingPrice()
                );
            }
            return;
        }

        if (amount.compareTo(auction.getCurrentHighestBid()) <= 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Bid must be greater than " + auction.getCurrentHighestBid()
            );
        }
    }

    private void validateCreateRequest(AuctionCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auction request is required");
        }
        if (request.listingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Listing id is required");
        }
        if (request.startingPrice() == null || request.startingPrice().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Starting price must be positive");
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

    private AuctionCommandResponse toAuctionResponse(Auction auction) {
        return new AuctionCommandResponse(
            auction.getId(),
            auction.getSellerId(),
            auction.getListingId(),
            auction.getStatus(),
            auction.getStartingPrice(),
            auction.getCurrentHighestBid(),
            auction.getCurrentHighestBidderId(),
            auction.getEndsAt(),
            auction.getCreatedAt(),
            auction.getUpdatedAt()
        );
    }

    private BidCommandResponse toBidResponse(Auction auction, Bid bid) {
        return new BidCommandResponse(
            auction.getId(),
            bid.getId(),
            bid.getBidderId(),
            bid.getAmount(),
            bid.getStatus(),
            auction.getStatus(),
            auction.getCurrentHighestBid(),
            auction.getCurrentHighestBidderId(),
            bid.getCreatedAt()
        );
    }

    private long defaultDuration(Long durationMinutes) {
        return durationMinutes == null ? DEFAULT_DURATION_MINUTES : durationMinutes;
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void publishSafely(Runnable publishAction) {
        try {
            publishAction.run();
        } catch (RuntimeException ex) {
            LOGGER.warn("Auction event publication failed", ex);
        }
    }
}
