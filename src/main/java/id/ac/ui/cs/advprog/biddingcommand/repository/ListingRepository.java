package id.ac.ui.cs.advprog.biddingcommand.repository;

import id.ac.ui.cs.advprog.biddingcommand.model.Listing;
import id.ac.ui.cs.advprog.biddingcommand.model.ListingStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, UUID> {
    long countBySellerIdAndStatus(UUID sellerId, ListingStatus status);
}
