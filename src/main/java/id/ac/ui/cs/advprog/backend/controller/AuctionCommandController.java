package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.backend.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.backend.service.AuctionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auctions")
public class AuctionCommandController {

    private final AuctionService auctionService;

    public AuctionCommandController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SELLER')")
    public AuctionDetailResponse createAuction(
        @Valid @RequestBody AuctionCreateRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionService.createAuction(request, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/activate")
    @PreAuthorize("hasRole('SELLER')")
    public AuctionDetailResponse activateAuction(
        @PathVariable UUID auctionId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionService.activateAuction(auctionId, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BUYER')")
    public AuctionDetailResponse placeBid(
        @PathVariable UUID auctionId,
        @Valid @RequestBody BidPlaceRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionService.placeBid(auctionId, request, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/close")
    @PreAuthorize("hasRole('SELLER')")
    public AuctionDetailResponse closeAuction(
        @PathVariable UUID auctionId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionService.closeAuction(auctionId, authenticatedUser.id());
    }
}
