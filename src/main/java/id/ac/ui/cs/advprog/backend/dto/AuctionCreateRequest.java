package id.ac.ui.cs.advprog.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record AuctionCreateRequest(
    @NotNull(message = "Listing id is required")
    UUID listingId,
    @NotNull(message = "Starting price is required")
    @DecimalMin(value = "0.01", message = "Starting price must be positive")
    BigDecimal startingPrice,
    Long durationMinutes,
    Boolean activateNow
) {
}
