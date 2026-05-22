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
    private static final String BASE_URL = "http://wallet.test";
    private static final String INTERNAL_TOKEN = "internal-token";
    private static final UUID USER_ID = UUID.fromString("77777777-1111-1111-1111-111111111111");
    private static final UUID AUCTION_ID = UUID.fromString("88888888-2222-2222-2222-222222222222");
    private static final BigDecimal AMOUNT = new BigDecimal("1000.00");

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private WalletClient walletClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        walletClient = new WalletClient(restTemplate, BASE_URL, INTERNAL_TOKEN);
    }

    @Test
    void holdFundsShouldPostToWalletHoldEndpoint() {
        expectWalletPost(HOLD_ENDPOINT);

        walletClient.holdFunds(USER_ID, AUCTION_ID, AMOUNT);

        server.verify();
    }

    @Test
    void releaseFundsShouldPostToWalletReleaseEndpoint() {
        expectWalletPost("/wallet/internal/release");

        walletClient.releaseFunds(USER_ID, AUCTION_ID, AMOUNT);

        server.verify();
    }

    @Test
    void captureFundsShouldPostToWalletCaptureEndpoint() {
        expectWalletPost("/wallet/internal/capture");

        walletClient.captureFunds(USER_ID, AUCTION_ID, AMOUNT);

        server.verify();
    }

    @Test
    void creditFundsShouldPostToWalletCreditEndpoint() {
        expectWalletPost("/wallet/internal/credit");

        walletClient.creditFunds(USER_ID, AUCTION_ID, AMOUNT);

        server.verify();
    }

    @Test
    void walletUnauthorizedShouldThrowSafeExceptionWithoutLeakingRawBody() {
        server.expect(requestTo(BASE_URL + HOLD_ENDPOINT))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret internal error"));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> walletClient.holdFunds(USER_ID, AUCTION_ID, AMOUNT)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Wallet service rejected the request", ex.getReason());
    }

    @Test
    void walletServerErrorShouldThrowSafeExceptionWithoutLeakingRawBody() {
        server.expect(requestTo(BASE_URL + HOLD_ENDPOINT))
            .andRespond(withServerError().body("database exploded"));

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> walletClient.holdFunds(USER_ID, AUCTION_ID, AMOUNT)
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        assertEquals("Wallet service error", ex.getReason());
    }

    private void expectWalletPost(String path) {
        server.expect(requestTo(BASE_URL + path))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.auctionId").value(AUCTION_ID.toString()))
            .andExpect(jsonPath("$.amount").value(1000.00))
            .andRespond(withSuccess());
    }

    @Test
    void shouldNotSendInternalTokenHeaderWhenInternalTokenBlank() {
        walletClient = new WalletClient(restTemplate, BASE_URL, "");

        server.expect(requestTo(BASE_URL + HOLD_ENDPOINT))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess());

        walletClient.holdFunds(USER_ID, AUCTION_ID, AMOUNT);

        server.verify();
    }
}
