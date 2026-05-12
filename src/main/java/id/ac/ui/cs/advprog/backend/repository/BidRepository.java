package id.ac.ui.cs.advprog.backend.repository;

import id.ac.ui.cs.advprog.backend.model.Bid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    @Query("""
        select count(b) > 0
        from Bid b
        where b.auction.listing.id = :listingId
        """)
    boolean existsByListingId(@Param("listingId") UUID listingId);

    long countByAuctionId(UUID auctionId);

    List<Bid> findByAuctionIdOrderBySequenceNumberAsc(UUID auctionId);

    Optional<Bid> findTopByAuctionIdOrderByAmountDescSequenceNumberAsc(UUID auctionId);
}
