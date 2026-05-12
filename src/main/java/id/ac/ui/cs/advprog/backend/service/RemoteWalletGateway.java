package id.ac.ui.cs.advprog.backend.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RemoteWalletGateway implements WalletGateway {

    private final RestTemplate restTemplate;
    private final String walletServiceBaseUrl;

    public RemoteWalletGateway(
        RestTemplate restTemplate,
        @Value("${WALLET_SERVICE_BASE_URL:http://localhost:8085}") String walletServiceBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.walletServiceBaseUrl = walletServiceBaseUrl;
    }

    @Override
    public void holdFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        postInternal("/wallet/internal/hold", new WalletInternalFundsRequest(userId, auctionId, amount));
    }

    @Override
    public void releaseFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        postInternal("/wallet/internal/release", new WalletInternalFundsRequest(userId, auctionId, amount));
    }

    @Override
    public void captureFunds(UUID userId, UUID auctionId, BigDecimal amount) {
        postInternal("/wallet/internal/capture", new WalletInternalFundsRequest(userId, auctionId, amount));
    }

    private void postInternal(String path, WalletInternalFundsRequest request) {
        try {
            restTemplate.exchange(walletServiceBaseUrl + path, HttpMethod.POST, new HttpEntity<>(request), Void.class);
        } catch (HttpStatusCodeException ex) {
            throw new ResponseStatusException(ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wallet service unavailable");
        }
    }
}
