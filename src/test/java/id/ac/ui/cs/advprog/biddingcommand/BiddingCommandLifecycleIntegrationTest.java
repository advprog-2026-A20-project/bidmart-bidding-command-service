package id.ac.ui.cs.advprog.biddingcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import id.ac.ui.cs.advprog.biddingcommand.service.BiddingCommandService;
import id.ac.ui.cs.advprog.biddingcommand.service.WalletClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:biddinglifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "auction.lifecycle.scan-interval-ms=3600000",
    "auction.lifecycle.closed-visible-seconds=5",
    "security.jwt.secret=test-secret-please-change-32-chars",
    "security.jwt.expiration-seconds=3600"
})
@ActiveProfiles("local")
@Transactional
class BiddingCommandLifecycleIntegrationTest {

    @Autowired
    private BiddingCommandService biddingCommandService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private WalletClient walletClient;

    private User seller;
    private User buyer;
    private User buyerB;

    @BeforeEach
    void setUp() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        listingRepository.deleteAll();
        userRepository.deleteAll();
        seller = userRepository.save(user("seller@example.com", Role.SELLER));
        buyer = userRepository.save(user("buyer@example.com", Role.BUYER));
        buyerB = userRepository.save(user("buyer-b@example.com", Role.BUYER));
    }

    @Test
    void expiredAuctionFirstMovesToVisibleClosedState() {
        Auction auction = auctionRepository.save(auction(AuctionStatus.ACTIVE, ListingStatus.ACTIVE, "1000.00"));
        auction.setEndsAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        auctionRepository.saveAndFlush(auction);

        biddingCommandService.processAuctionLifecycle();

        Auction refreshed = auctionRepository.findByIdWithListingAndSeller(auction.getId()).orElseThrow();
        assertEquals(AuctionStatus.CLOSED, refreshed.getStatus());
        assertEquals(ListingStatus.CLOSED, refreshed.getListing().getStatus());
        verify(walletClient, never()).captureFunds(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void closedAuctionWithReserveMetResolvesWonAndCreditsSeller() {
        Auction auction = auctionRepository.save(auction(AuctionStatus.CLOSED, ListingStatus.CLOSED, "1000.00"));
        auction.setClosedAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        bidRepository.save(bid(auction, buyer, "1500.00"));
        auctionRepository.saveAndFlush(auction);

        biddingCommandService.processAuctionLifecycle();

        Auction refreshed = auctionRepository.findByIdWithListingAndSeller(auction.getId()).orElseThrow();
        assertEquals(AuctionStatus.WON, refreshed.getStatus());
        assertEquals(ListingStatus.WON, refreshed.getListing().getStatus());
        verify(walletClient).captureFunds(buyer.getId(), auction.getId(), new BigDecimal("1500.00"));
        verify(walletClient).creditFunds(seller.getId(), auction.getId(), new BigDecimal("1500.00"));
    }

    @Test
    void closedAuctionBelowReserveResolvesUnsoldAndReleasesBuyer() {
        Auction auction = auctionRepository.save(auction(AuctionStatus.CLOSED, ListingStatus.CLOSED, "2000.00"));
        auction.setClosedAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        bidRepository.save(bid(auction, buyer, "1500.00"));
        auctionRepository.saveAndFlush(auction);

        biddingCommandService.processAuctionLifecycle();

        Auction refreshed = auctionRepository.findByIdWithListingAndSeller(auction.getId()).orElseThrow();
        assertEquals(AuctionStatus.UNSOLD, refreshed.getStatus());
        assertEquals(ListingStatus.UNSOLD, refreshed.getListing().getStatus());
        verify(walletClient).releaseFunds(buyer.getId(), auction.getId(), new BigDecimal("1500.00"));
    }

    @Test
    void higherBidFromDifferentBuyerHoldsNewBidAndReleasesPreviousLeader() {
        Auction auction = auctionRepository.save(auction(AuctionStatus.ACTIVE, ListingStatus.ACTIVE, "1000.00"));
        auction.setEndsAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        auctionRepository.saveAndFlush(auction);

        biddingCommandService.placeBid(auction.getId(), new id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest(new BigDecimal("1000.00")), buyer.getId());
        biddingCommandService.placeBid(auction.getId(), new id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest(new BigDecimal("2000.00")), buyerB.getId());

        verify(walletClient).holdFunds(buyer.getId(), auction.getId(), new BigDecimal("1000.00"));
        verify(walletClient).holdFunds(buyerB.getId(), auction.getId(), new BigDecimal("2000.00"));
        verify(walletClient).releaseFunds(buyer.getId(), auction.getId(), new BigDecimal("1000.00"));
    }

    @Test
    void higherBidFromSameBuyerOnlyHoldsDifferenceWithoutRelease() {
        Auction auction = auctionRepository.save(auction(AuctionStatus.ACTIVE, ListingStatus.ACTIVE, "1000.00"));
        auction.setEndsAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        auctionRepository.saveAndFlush(auction);

        biddingCommandService.placeBid(auction.getId(), new id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest(new BigDecimal("1000.00")), buyer.getId());
        biddingCommandService.placeBid(auction.getId(), new id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest(new BigDecimal("2000.00")), buyer.getId());

        verify(walletClient, times(2)).holdFunds(buyer.getId(), auction.getId(), new BigDecimal("1000.00"));
        verify(walletClient, never()).releaseFunds(
            org.mockito.ArgumentMatchers.eq(buyer.getId()),
            org.mockito.ArgumentMatchers.eq(auction.getId()),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private User user(String email, Role role) {
        return User.builder()
            .email(email)
            .passwordHash("hash")
            .role(role)
            .createdAt(Instant.now())
            .build();
    }

    private Auction auction(AuctionStatus auctionStatus, ListingStatus listingStatus, String reservePrice) {
        Listing listing = listingRepository.save(Listing.builder()
            .title("Phone")
            .description("Auction phone")
            .price(new BigDecimal("1000.00"))
            .category(ListingCategory.ELECTRONICS)
            .seller(seller)
            .status(listingStatus)
            .createdAt(Instant.now())
            .build());
        Instant now = Instant.now();
        return Auction.builder()
            .listing(listing)
            .status(auctionStatus)
            .startingPrice(new BigDecimal("1000.00"))
            .reservePrice(new BigDecimal(reservePrice))
            .minimumBidIncrement(new BigDecimal("100.00"))
            .durationMinutes(1L)
            .nextBidSequence(2L)
            .extensionCount(0)
            .createdAt(now.minus(2, ChronoUnit.MINUTES))
            .startsAt(now.minus(2, ChronoUnit.MINUTES))
            .endsAt(now.minus(1, ChronoUnit.MINUTES))
            .build();
    }

    private Bid bid(Auction auction, User bidder, String amount) {
        return Bid.builder()
            .auction(auction)
            .bidder(bidder)
            .amount(new BigDecimal(amount))
            .sequenceNumber(1L)
            .submittedAt(Instant.now().minus(30, ChronoUnit.SECONDS))
            .build();
    }
}
