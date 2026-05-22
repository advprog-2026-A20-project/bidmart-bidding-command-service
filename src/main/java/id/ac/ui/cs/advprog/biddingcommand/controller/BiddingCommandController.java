package id.ac.ui.cs.advprog.biddingcommand.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionCreateRequest;
import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.dto.BidPlaceRequest;
import id.ac.ui.cs.advprog.biddingcommand.security.AuthenticatedUser;
import id.ac.ui.cs.advprog.biddingcommand.service.BiddingCommandService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auctions")
public class BiddingCommandController {

    private final BiddingCommandService biddingCommandService;

    public BiddingCommandController(BiddingCommandService biddingCommandService) {
        this.biddingCommandService = biddingCommandService;
    }

    @GetMapping("/{auctionId}")
    public AuctionDetailResponse getAuctionDetail(@PathVariable UUID auctionId) {
        return biddingCommandService.getAuctionDetail(auctionId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SELLER')")
    public AuctionDetailResponse createAuction(
        @Valid @RequestBody AuctionCreateRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return biddingCommandService.createAuction(request, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/activate")
    @PreAuthorize("hasRole('SELLER')")
    public AuctionDetailResponse activateAuction(
        @PathVariable UUID auctionId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return biddingCommandService.activateAuction(auctionId, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/bids")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BUYER')")
    public AuctionDetailResponse placeBid(
        @PathVariable UUID auctionId,
        @Valid @RequestBody BidPlaceRequest request,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return biddingCommandService.placeBid(auctionId, request, authenticatedUser.id());
    }

    @PostMapping("/{auctionId}/close")
    @PreAuthorize("hasRole('SELLER')")
    public AuctionDetailResponse closeAuction(
        @PathVariable UUID auctionId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return biddingCommandService.closeAuction(auctionId, authenticatedUser.id());
    }
}