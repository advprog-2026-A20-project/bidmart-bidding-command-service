package id.ac.ui.cs.advprog.backend.controller;

import id.ac.ui.cs.advprog.backend.application.service.AuctionCommandService;
import id.ac.ui.cs.advprog.backend.dto.AuctionCommandResponse;
import id.ac.ui.cs.advprog.backend.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.backend.dto.BidCommandResponse;
import id.ac.ui.cs.advprog.backend.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.backend.security.AuthenticatedUser;
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

    private final AuctionCommandService auctionCommandService;

    public AuctionCommandController(AuctionCommandService auctionCommandService) {
        this.auctionCommandService = auctionCommandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SELLER')")
    public AuctionCommandResponse createAuction(
        @Valid @RequestBody AuctionCreateRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionCommandService.createAuction(request, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/activate")
    @PreAuthorize("hasRole('SELLER')")
    public AuctionCommandResponse activateAuction(
        @PathVariable UUID auctionId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionCommandService.activateAuction(auctionId, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BUYER')")
    public BidCommandResponse placeBid(
        @PathVariable UUID auctionId,
        @Valid @RequestBody BidPlaceRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionCommandService.placeBid(auctionId, request, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/close")
    @PreAuthorize("hasRole('SELLER')")
    public AuctionCommandResponse closeAuction(
        @PathVariable UUID auctionId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return auctionCommandService.closeAuction(auctionId, authenticatedUser.id());
    }
}
