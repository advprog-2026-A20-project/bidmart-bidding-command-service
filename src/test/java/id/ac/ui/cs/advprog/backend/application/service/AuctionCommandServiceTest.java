package id.ac.ui.cs.advprog.backend.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import id.ac.ui.cs.advprog.backend.application.port.WalletClient;
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
class AuctionCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-18T00:00:00Z");

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private WalletClient walletClient;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    private AuctionCommandService service;

    @BeforeEach
    void setUp() {
        service = new AuctionCommandService(
            auctionRepository,
            bidRepository,
            walletClient,
            auctionEventPublisher,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void placeBid_success_whenAuctionActiveAndWalletHoldSucceeds() {
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID previousBidderId = UUID.randomUUID();
        UUID bidderId = UUID.randomUUID();
        Auction auction = activeAuction(auctionId, sellerId, BigDecimal.valueOf(100), previousBidderId);
        Bid previousWinningBid = Bid.builder()
            .id(UUID.randomUUID())
            .auctionId(auctionId)
            .bidderId(previousBidderId)
            .amount(BigDecimal.valueOf(100))
            .status(BidStatus.WINNING)
            .createdAt(NOW.minusSeconds(60))
            .build();

        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatus(auctionId, BidStatus.WINNING))
            .thenReturn(Optional.of(previousWinningBid));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            if (bid.getId() == null) {
                bid.setId(UUID.randomUUID());
            }
            return bid;
        });
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BidCommandResponse response = service.placeBid(auctionId, new BidPlaceRequest(BigDecimal.valueOf(150)), bidderId);

        assertThat(response.bidderId()).isEqualTo(bidderId);
        assertThat(response.amount()).isEqualByComparingTo("150.00");
        assertThat(response.bidStatus()).isEqualTo(BidStatus.WINNING);
        assertThat(response.currentHighestBidderId()).isEqualTo(bidderId);
        assertThat(auction.getCurrentHighestBid()).isEqualByComparingTo("150.00");
        assertThat(previousWinningBid.getStatus()).isEqualTo(BidStatus.OUTBID);
        verify(walletClient).hold(bidderId, auctionId, BigDecimal.valueOf(150).setScale(2));
        verify(walletClient).release(previousBidderId, auctionId, BigDecimal.valueOf(100));
    }

    @Test
    void placeBid_rejects_whenAuctionMissing() {
        UUID auctionId = UUID.randomUUID();
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeBid(auctionId, new BidPlaceRequest(BigDecimal.valueOf(150)), UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void placeBid_rejects_whenAuctionNotActive() {
        UUID auctionId = UUID.randomUUID();
        Auction auction = activeAuction(auctionId, UUID.randomUUID(), BigDecimal.valueOf(100), null);
        auction.setStatus(AuctionStatus.DRAFT);
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> service.placeBid(auctionId, new BidPlaceRequest(BigDecimal.valueOf(150)), UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void placeBid_rejects_whenBidderIsSeller() {
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(auctionRepository.findById(auctionId))
            .thenReturn(Optional.of(activeAuction(auctionId, sellerId, BigDecimal.valueOf(100), null)));

        assertThatThrownBy(() -> service.placeBid(auctionId, new BidPlaceRequest(BigDecimal.valueOf(150)), sellerId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void placeBid_rejects_whenAmountTooLow() {
        UUID auctionId = UUID.randomUUID();
        UUID bidderId = UUID.randomUUID();
        when(auctionRepository.findById(auctionId))
            .thenReturn(Optional.of(activeAuction(auctionId, UUID.randomUUID(), BigDecimal.valueOf(100), null)));

        assertThatThrownBy(() -> service.placeBid(auctionId, new BidPlaceRequest(BigDecimal.valueOf(100)), bidderId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        verify(walletClient, never()).hold(any(), any(), any());
    }

    @Test
    void placeBid_doesNotPersist_whenWalletHoldFails() {
        UUID auctionId = UUID.randomUUID();
        UUID bidderId = UUID.randomUUID();
        Auction auction = activeAuction(auctionId, UUID.randomUUID(), BigDecimal.valueOf(100), null);
        BigDecimal amount = BigDecimal.valueOf(150).setScale(2);
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wallet service unavailable"))
            .when(walletClient).hold(bidderId, auctionId, amount);

        assertThatThrownBy(() -> service.placeBid(auctionId, new BidPlaceRequest(BigDecimal.valueOf(150)), bidderId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(bidRepository, never()).save(any(Bid.class));
        verify(auctionRepository, never()).save(any(Auction.class));
        assertThat(auction.getCurrentHighestBidderId()).isNull();
    }

    private Auction activeAuction(UUID auctionId, UUID sellerId, BigDecimal currentHighestBid, UUID currentHighestBidderId) {
        return Auction.builder()
            .id(auctionId)
            .sellerId(sellerId)
            .listingId(UUID.randomUUID())
            .status(AuctionStatus.ACTIVE)
            .startingPrice(BigDecimal.valueOf(100))
            .currentHighestBid(currentHighestBid)
            .currentHighestBidderId(currentHighestBidderId)
            .endsAt(NOW.plusSeconds(3600))
            .createdAt(NOW.minusSeconds(3600))
            .updatedAt(NOW.minusSeconds(3600))
            .build();
    }
}
