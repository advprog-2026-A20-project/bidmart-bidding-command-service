package id.ac.ui.cs.advprog.backend.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuctionExpiryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionExpiryScheduler.class);

    private final AuctionService auctionService;

    public AuctionExpiryScheduler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @Scheduled(fixedDelayString = "${auction.expired-close-delay-ms:1000}")
    public void closeExpiredAuctions() {
        for (UUID auctionId : auctionService.findExpiredOpenAuctionIds()) {
            try {
                auctionService.closeExpiredAuction(auctionId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to close expired auction {}", auctionId, exception);
            }
        }
    }
}
