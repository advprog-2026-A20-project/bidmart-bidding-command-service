package id.ac.ui.cs.advprog.backend.dto;

import id.ac.ui.cs.advprog.backend.model.ListingCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AuctionCreateRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    String title,
    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    String description,
    @Size(max = 1000, message = "Image URL must be at most 1000 characters")
    String imageUrl,
    ListingCategory category,
    @NotNull(message = "Starting price is required")
    @DecimalMin(value = "0.01", message = "Starting price must be positive")
    BigDecimal startingPrice,
    @NotNull(message = "Reserve price is required")
    @DecimalMin(value = "0.01", message = "Reserve price must be positive")
    BigDecimal reservePrice,
    @DecimalMin(value = "0.01", message = "Minimum bid increment must be positive")
    BigDecimal minimumBidIncrement,
    Long durationMinutes,
    Boolean activateNow
) {
}

