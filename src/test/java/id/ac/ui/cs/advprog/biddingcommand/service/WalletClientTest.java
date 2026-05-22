package id.ac.ui.cs.advprog.biddingcommand.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

class WalletClientTest {

    private static final String BASE_URL = "http://wallet-service.test";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private WalletClient walletClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        walletClient = new WalletClient(restTemplate, BASE_URL, "internal-secret");
    }

    @Test
    void holdFunds_shouldPostToWalletHoldEndpoint() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID auctionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        server.expect(once(), requestTo(BASE_URL + "/wallet/internal/hold"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Internal-Token", "internal-secret"))
            .andExpect(content().json("""
                {
                  "userId":"11111111-1111-1111-1111-111111111111",
                  "auctionId":"22222222-2222-2222-2222-222222222222",
                  "amount":1000.00
                }
                """))
            .andRespond(withSuccess());

        walletClient.holdFunds(userId, auctionId, new BigDecimal("1000.00"));

        server.verify();
    }

    @Test
    void releaseFunds_shouldPostToWalletReleaseEndpoint() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID auctionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        server.expect(once(), requestTo(BASE_URL + "/wallet/internal/release"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess());

        walletClient.releaseFunds(userId, auctionId, new BigDecimal("1000.00"));

        server.verify();
    }

    @Test
    void captureFunds_shouldPostToWalletCaptureEndpoint() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID auctionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        server.expect(once(), requestTo(BASE_URL + "/wallet/internal/capture"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess());

        walletClient.captureFunds(userId, auctionId, new BigDecimal("1000.00"));

        server.verify();
    }

    @Test
    void walletUnavailable_shouldThrowBadGateway() {
        WalletClient unavailableClient = new WalletClient(new RestTemplate() {
            @Override
            public <T> org.springframework.http.ResponseEntity<T> postForEntity(
                String url,
                Object request,
                Class<T> responseType,
                Object... uriVariables
            ) {
                throw new ResourceAccessException("connection refused");
            }
        }, BASE_URL, "internal-secret");

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> unavailableClient.holdFunds(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"))
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        assertEquals("Wallet service unavailable", exception.getReason());
    }

    @Test
    void wallet4xx_shouldThrowSafeResponseStatusExceptionWithoutLeakingRawBody() {
        server.expect(once(), requestTo(BASE_URL + "/wallet/internal/hold"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"wallet debug detail\",\"trace\":\"secret\"}"));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> walletClient.holdFunds(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Wallet service rejected the request", exception.getReason());
        assertFalse(exception.getReason().contains("wallet debug detail"));
        assertFalse(exception.getReason().contains("trace"));
    }

    @Test
    void wallet5xx_shouldThrowSafeResponseStatusExceptionWithoutLeakingRawBody() {
        server.expect(once(), requestTo(BASE_URL + "/wallet/internal/hold"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body("database hostname wallet-prod-01"));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> walletClient.holdFunds(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"))
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertEquals("Wallet service error", exception.getReason());
        assertFalse(exception.getReason().contains("wallet-prod-01"));
    }
}
