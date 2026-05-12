package id.ac.ui.cs.advprog.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BidResponse(
    UUID id,
    UUID bidderId,
    String bidderEmail,
    BigDecimal amount,
    Long sequenceNumber,
    Instant submittedAt,
    boolean winning
) {
}

