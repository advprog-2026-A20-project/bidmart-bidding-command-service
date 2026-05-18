package id.ac.ui.cs.advprog.backend.repository;

import id.ac.ui.cs.advprog.backend.model.Auction;
import id.ac.ui.cs.advprog.backend.model.AuctionStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    boolean existsByListingId(UUID listingId);
    Optional<Auction> findByListingId(UUID listingId);

    long countBySellerIdAndStatusIn(UUID sellerId, Collection<AuctionStatus> statuses);
}
