package id.ac.ui.cs.advprog.biddingcommand.service;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidResponse;
import id.ac.ui.cs.advprog.biddingcommand.model.Auction;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Bid;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import id.ac.ui.cs.advprog.biddingcommand.repository.AuctionRepository;
import id.ac.ui.cs.advprog.biddingcommand.repository.BidRepository;
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

    private static final Duration LAST_MINUTE_EXTENSION_WINDOW = Duration.ofMinutes(2);
    private static final Comparator<Bid> LEADING_BID_COMPARATOR = Comparator
        .comparing(Bid::getAmount)
        .thenComparing(Bid::getSequenceNumber, Comparator.reverseOrder());

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
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
        UserRepository userRepository,
        WalletClient walletClient,
        Clock clock
    ) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
        this.walletClient = walletClient;
        this.clock = clock;
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
}
