package id.ac.ui.cs.advprog.backend.service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class InMemoryListingPriceUpdateQueue {

    private final BlockingQueue<ListingPriceUpdateMessage> queue = new LinkedBlockingQueue<>();
    private final ListingService listingService;

    public InMemoryListingPriceUpdateQueue(ListingService listingService) {
        this.listingService = listingService;
    }

    public void publish(ListingPriceUpdateMessage message) {
        if (message != null) {
            queue.offer(message);
        }
    }

    @Scheduled(fixedDelayString = "${listing.price-update.consumer-delay-ms:250}")
    public void consumePendingUpdates() {
        ListingPriceUpdateMessage message = queue.poll();
        while (message != null) {
            listingService.updateDisplayedPrice(message.listingId(), message.latestPrice());
            message = queue.poll();
        }
    }

    public void flushPendingUpdates() {
        consumePendingUpdates();
    }

    public int pendingCount() {
        return queue.size();
    }
}
