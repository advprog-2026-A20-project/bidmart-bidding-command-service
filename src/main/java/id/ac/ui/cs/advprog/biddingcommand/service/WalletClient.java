package id.ac.ui.cs.advprog.biddingcommand.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import id.ac.ui.cs.advprog.biddingcommand.dto.WalletCommandRequest;

@Component
public class WalletClient {

    private final RestTemplate restTemplate;
    private final String walletBaseUrl;
    private final String internalToken;

    public WalletClient(
        RestTemplate restTemplate,
        @Value("${wallet.service.base-url:${WALLET_SERVICE_BASE_URL:http://localhost:8084}}")
        String walletBaseUrl,
        @Value("${internal.service-token:${INTERNAL_SERVICE_TOKEN:}}")
        String internalToken
    ) {
        this.restTemplate = restTemplate;
        this.walletBaseUrl = trimTrailingSlash(walletBaseUrl);
        this.internalToken = internalToken;
    }

    public void holdFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        post("/wallet/internal/hold", userId, auctionId, amount);
    }

    public void releaseFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        post("/wallet/internal/release", userId, auctionId, amount);
    }

    public void captureFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        post("/wallet/internal/capture", userId, auctionId, amount);
    }

    public void creditFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        post("/wallet/internal/credit", userId, auctionId, amount);
    }

    private void post(String path, UUID userId, UUID auctionId, BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        if (internalToken != null && !internalToken.isBlank()) {
            headers.set("X-Internal-Token", internalToken);
        }

        WalletCommandRequest request = new WalletCommandRequest(userId, auctionId, amount);

        try {
            restTemplate.postForEntity(
                walletBaseUrl + path,
                new HttpEntity<>(request, headers),
                Void.class
            );
        } catch (HttpStatusCodeException ex) {
            throw new ResponseStatusException(
                ex.getStatusCode(),
                safeWalletErrorMessage(ex)
            );
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Wallet service unavailable"
            );
        }
    }

    private String safeWalletErrorMessage(HttpStatusCodeException ex) {
        if (ex.getStatusCode().is4xxClientError()) {
            return "Wallet service rejected the request";
        }
        return "Wallet service error";
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8084";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
