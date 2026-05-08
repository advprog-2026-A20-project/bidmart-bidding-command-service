package id.ac.ui.cs.advprog.biddingcommand.api;

import id.ac.ui.cs.advprog.biddingcommand.api.dto.CloseAuctionRequest;
import id.ac.ui.cs.advprog.biddingcommand.api.dto.PlaceBidRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BiddingCommandController {

    @PostMapping("/bids")
    public ResponseEntity<Map<String, Object>> placeBid(@RequestBody PlaceBidRequest request) {
        String bidId = UUID.randomUUID().toString();
        return ResponseEntity.created(URI.create("/api/bids/" + bidId))
                .body(Map.of(
                        "status", "ACCEPTED",
                        "message", "Bid command diterima untuk diproses.",
                        "bidId", bidId,
                        "auctionId", request.auctionId()
                ));
    }

    @PostMapping("/auctions/{auctionId}/bids")
    public ResponseEntity<Map<String, Object>> placeBidToAuction(
            @PathVariable String auctionId,
            @RequestBody PlaceBidRequest request
    ) {
        String bidId = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "status", "ACCEPTED",
                        "message", "Bid command untuk auction diterima.",
                        "bidId", bidId,
                        "auctionId", auctionId,
                        "requestedAuctionId", request.auctionId()
                ));
    }

    @PostMapping("/auctions/{auctionId}/close")
    public ResponseEntity<Map<String, Object>> closeAuction(
            @PathVariable String auctionId,
            @RequestBody(required = false) CloseAuctionRequest request
    ) {
        String reason = request == null ? "SCHEDULED_END" : request.reason();
        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "message", "Close auction command diterima.",
                "auctionId", auctionId,
                "reason", reason
        ));
    }

    @PostMapping("/auctions/{auctionId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelAuction(@PathVariable String auctionId) {
        return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "message", "Cancel auction command diterima.",
                "auctionId", auctionId
        ));
    }
}
