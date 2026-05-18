package id.ac.ui.cs.advprog.backend.repository;

import id.ac.ui.cs.advprog.backend.model.Bid;
import id.ac.ui.cs.advprog.backend.model.BidStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    long countByAuctionId(UUID auctionId);

    List<Bid> findByAuctionIdOrderByCreatedAtAsc(UUID auctionId);

    Optional<Bid> findFirstByAuctionIdAndStatus(UUID auctionId, BidStatus status);
}
