package id.ac.ui.cs.advprog.biddingcommand.service;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

class WalletClientTest {

    private static final String HOLD_ENDPOINT = "/wallet/internal/hold";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private WalletClient walletClient;

    private final String baseUrl = "http://wallet.test";
    private final String token = "internal-token";
    private final UUID userId = UUID.randomUUID();
    private final UUID auctionId = UUID.randomUUID();
    private final BigDecimal amount = new BigDecimal("1000.00");

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        walletClient = new WalletClient(restTemplate, baseUrl, token);
    }

    @Test
    void holdFundsShouldPostToWalletHoldEndpoint() {
        expectWalletPost(HOLD_ENDPOINT);

        walletClient.holdFunds(userId, auctionId, amount);

        server.verify();
    }

    @Test
    void releaseFundsShouldPostToWalletReleaseEndpoint() {
        expectWalletPost("/wallet/internal/release");

        walletClient.releaseFunds(userId, auctionId, amount);

        server.verify();
    }

    @Test
    void captureFundsShouldPostToWalletCaptureEndpoint() {
        expectWalletPost("/wallet/internal/capture");

        walletClient.captureFunds(userId, auctionId, amount);

        server.verify();
    }

    @Test
    void creditFundsShouldPostToWalletCreditEndpoint() {
        expectWalletPost("/wallet/internal/credit");

        walletClient.creditFunds(userId, auctionId, amount);

        server.verify();
    }

    @Test
    void walletUnauthorizedShouldThrowSafeExceptionWithoutLeakingRawBody() {
        server.expect(requestTo(baseUrl + HOLD_ENDPOINT))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret internal error"));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> walletClient.holdFunds(userId, auctionId, amount)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Wallet service rejected the request", ex.getReason());
    }

    @Test
    void walletServerErrorShouldThrowSafeExceptionWithoutLeakingRawBody() {
        server.expect(requestTo(baseUrl + HOLD_ENDPOINT))
            .andRespond(withServerError().body("database exploded"));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> walletClient.holdFunds(userId, auctionId, amount)
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        assertEquals("Wallet service error", ex.getReason());
    }

    private void expectWalletPost(String path) {
        server.expect(requestTo(baseUrl + path))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Internal-Token", token))
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.auctionId").value(auctionId.toString()))
            .andExpect(jsonPath("$.amount").value(1000.00))
            .andRespond(withSuccess());
    }

    @Test
    void shouldNotSendInternalTokenHeaderWhenInternalTokenBlank() {
        walletClient = new WalletClient(restTemplate, baseUrl, "");

        server.expect(requestTo(baseUrl + HOLD_ENDPOINT))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess());

        walletClient.holdFunds(userId, auctionId, amount);

        server.verify();
    }
}
