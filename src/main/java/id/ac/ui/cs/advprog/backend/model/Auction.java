package id.ac.ui.cs.advprog.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "auction")
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "listing_id", nullable = false, unique = true)
    private Listing listing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal startingPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal reservePrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumBidIncrement;

    @Column(nullable = false)
    private Long durationMinutes;

    @Column(nullable = false)
    private Long nextBidSequence;

    @Column(nullable = false)
    private Integer extensionCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant activatedAt;

    @Column
    private Instant startsAt;

    @Column
    private Instant endsAt;

    @Column
    private Instant closedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (nextBidSequence == null || nextBidSequence < 1) {
            nextBidSequence = 1L;
        }
        if (extensionCount == null || extensionCount < 0) {
            extensionCount = 0;
        }
    }
}
