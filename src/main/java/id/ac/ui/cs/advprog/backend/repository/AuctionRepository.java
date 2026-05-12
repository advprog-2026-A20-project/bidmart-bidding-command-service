package id.ac.ui.cs.advprog.backend.repository;

import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    boolean existsByListingId(UUID listingId);
    Optional<Auction> findByListingId(UUID listingId);

    long countByListingSellerIdAndStatusIn(UUID sellerId, Collection<AuctionStatus> statuses);

    @Query("""
        select a
        from Auction a
        join fetch a.listing l
        join fetch l.seller
        order by a.createdAt desc
        """)
    List<Auction> findAllWithListingAndSellerOrderByCreatedAtDesc();

    @Query("""
        select a
        from Auction a
        join fetch a.listing l
        join fetch l.seller
        where a.id = :auctionId
        """)
    Optional<Auction> findByIdWithListingAndSeller(@Param("auctionId") UUID auctionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from Auction a
        join fetch a.listing l
        join fetch l.seller
        where a.id = :auctionId
        """)
    Optional<Auction> findByIdWithListingAndSellerForUpdate(@Param("auctionId") UUID auctionId);
}
