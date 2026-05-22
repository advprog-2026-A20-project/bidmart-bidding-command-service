package id.ac.ui.cs.advprog.biddingcommand.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import id.ac.ui.cs.advprog.biddingcommand.dto.AuctionDetailResponse;
import id.ac.ui.cs.advprog.biddingcommand.model.AuctionStatus;
import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import id.ac.ui.cs.advprog.biddingcommand.service.BiddingCommandService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:securitybehavior;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "security.jwt.secr" + "et=abcdefghijklmnopqrstuvwxyz123456",
    "security.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
class SecurityBehaviorTest {

    private static final String JWT_SIGNING_KEY = "abcdefghijklmnopqrstuvwxyz123456";
    private static final String AUCTIONS_ENDPOINT = "/api/auctions";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final UUID AUCTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final BigDecimal AMOUNT_1000 = new BigDecimal("1000.00");
    private static final BigDecimal AMOUNT_1500 = new BigDecimal("1500.00");
    private static final BigDecimal AMOUNT_0100 = new BigDecimal("100.00");
    private static final BigDecimal AMOUNT_1100 = new BigDecimal("1100.00");
    private static final Instant DUMMY_NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final AuctionDetailResponse DUMMY_RESPONSE = new AuctionDetailResponse(
        AUCTION_ID,
        UUID.fromString("55555555-5555-5555-5555-555555555555"),
        "Vintage Camera",
        "Working condition",
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        "seller@bidmart.test",
        AMOUNT_1000,
        AMOUNT_1000,
        AMOUNT_1500,
        AMOUNT_0100,
        AuctionStatus.DRAFT,
        DUMMY_NOW,
        DUMMY_NOW.plusSeconds(60),
        DUMMY_NOW.plusSeconds(3660),
        null,
        60L,
        0,
        0L,
        AMOUNT_1100,
        false,
        true,
        null,
        null,
        List.of()
    );
    private static final String AUCTION_CREATE_REQUEST_JSON = """
        {
          "title": "Vintage Camera",
          "description": "Working condition",
          "imageUrl": "https://example.com/camera.jpg",
          "category": "ELECTRONICS",
          "startingPrice": 1000.00,
          "reservePrice": 1500.00,
          "minimumBidIncrement": 100.00,
          "durationMinutes": 60,
          "activateNow": false
        }
        """;
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BiddingCommandService biddingCommandService;

    @BeforeEach
    void setUp() {
        given(biddingCommandService.createAuction(any(), any(UUID.class))).willReturn(DUMMY_RESPONSE);
        given(biddingCommandService.placeBid(any(UUID.class), any(), any(UUID.class))).willReturn(DUMMY_RESPONSE);
        given(biddingCommandService.activateAuction(any(UUID.class), any(UUID.class))).willReturn(DUMMY_RESPONSE);
        given(biddingCommandService.closeAuction(any(UUID.class), any(UUID.class))).willReturn(DUMMY_RESPONSE);
    }

    @Test
    void publicEndpointHealthShouldBeAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(post(AUCTIONS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(AUCTION_CREATE_REQUEST_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(unauthenticated());
    }

    @Test
    void createAuctionWithSellerTokenShouldPassSecurityLayer() throws Exception {
        String token = jwtService.generateToken(user(Role.SELLER));

        mockMvc.perform(post(AUCTIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(AUCTION_CREATE_REQUEST_JSON))
            .andExpect(status().isCreated());
    }

    @Test
    void createAuctionWithBuyerTokenShouldReturn403() throws Exception {
        String token = jwtService.generateToken(user(Role.BUYER));

        mockMvc.perform(post(AUCTIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(AUCTION_CREATE_REQUEST_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    void placeBidWithBuyerTokenShouldPassSecurityLayer() throws Exception {
        String token = jwtService.generateToken(user(Role.BUYER));

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", AUCTION_ID)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1500.00}"))
            .andExpect(status().isCreated());

        verify(biddingCommandService).placeBid(any(UUID.class), any(), any(UUID.class));
    }

    @Test
    void placeBidWithSellerTokenShouldReturn403() throws Exception {
        String token = jwtService.generateToken(user(Role.SELLER));

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", AUCTION_ID)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1500.00}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void placeBidWithoutAmountShouldReturn400() throws Exception {
        String token = jwtService.generateToken(user(Role.BUYER));

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", AUCTION_ID)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.amount").value("Bid amount is required"));

        verify(biddingCommandService, never()).placeBid(any(UUID.class), any(), any(UUID.class));
    }

    @Test
    void placeBidWithZeroAmountShouldReturn400() throws Exception {
        String token = jwtService.generateToken(user(Role.BUYER));

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", AUCTION_ID)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.amount").value("Bid amount must be positive"));

        verify(biddingCommandService, never()).placeBid(any(UUID.class), any(), any(UUID.class));
    }

    @Test
    void placeBidWithNegativeAmountShouldReturn400() throws Exception {
        String token = jwtService.generateToken(user(Role.BUYER));

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", AUCTION_ID)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":-1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.amount").value("Bid amount must be positive"));

        verify(biddingCommandService, never()).placeBid(any(UUID.class), any(), any(UUID.class));
    }

    @Test
    void malformedTokenShouldReturn401Not500() throws Exception {
        mockMvc.perform(post(AUCTIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + "malformed.token.value")
                .contentType(MediaType.APPLICATION_JSON)
                .content(AUCTION_CREATE_REQUEST_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(unauthenticated());
    }

    @Test
    void tokenWithInvalidRoleClaimShouldReturn401Not500() throws Exception {
        String token = Jwts.builder()
            .setSubject(UUID.fromString("33333333-3333-3333-3333-333333333333").toString())
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("email", "seller@bidmart.test")
            .claim("role", "INVALID_ROLE")
            .signWith(
                Keys.hmacShaKeyFor(JWT_SIGNING_KEY.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256
            )
            .compact();

        mockMvc.perform(post(AUCTIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(AUCTION_CREATE_REQUEST_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(unauthenticated());
    }

    private User user(Role role) {
        return User.builder()
            .id(UUID.fromString(role == Role.SELLER
                ? "11111111-1111-1111-1111-111111111111"
                : "44444444-4444-4444-4444-444444444444"))
            .email(role.name().toLowerCase() + "@bidmart.test")
            .passwordHash("hash")
            .role(role)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();
    }

}
