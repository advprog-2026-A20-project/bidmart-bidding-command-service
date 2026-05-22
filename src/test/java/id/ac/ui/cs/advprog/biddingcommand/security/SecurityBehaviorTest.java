package id.ac.ui.cs.advprog.biddingcommand.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    "security.jwt.secret=test-secret-please-change-32-chars",
    "security.jwt.expiration-seconds=3600"
})
@AutoConfigureMockMvc
class SecurityBehaviorTest {

    private static final String JWT_SECRET = "test-secret-please-change-32-chars";
    private static final UUID AUCTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BiddingCommandService biddingCommandService;

    @BeforeEach
    void setUp() {
        AuctionDetailResponse response = dummyAuctionDetailResponse();
        given(biddingCommandService.createAuction(any(), any(UUID.class))).willReturn(response);
        given(biddingCommandService.placeBid(any(UUID.class), any(), any(UUID.class))).willReturn(response);
        given(biddingCommandService.activateAuction(any(UUID.class), any(UUID.class))).willReturn(response);
        given(biddingCommandService.closeAuction(any(UUID.class), any(UUID.class))).willReturn(response);
    }

    @Test
    void publicEndpoint_health_shouldBeAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auctions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validAuctionCreateRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(unauthenticated());
    }

    @Test
    void createAuction_withSellerToken_shouldPassSecurityLayer() throws Exception {
        String token = jwtService.generateToken(user(Role.SELLER));

        mockMvc.perform(post("/api/auctions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validAuctionCreateRequest()))
            .andExpect(status().isCreated());
    }

    @Test
    void createAuction_withBuyerToken_shouldReturn403() throws Exception {
        String token = jwtService.generateToken(user(Role.BUYER));

        mockMvc.perform(post("/api/auctions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validAuctionCreateRequest()))
            .andExpect(status().isForbidden());
    }

    @Test
    void placeBid_withBuyerToken_shouldPassSecurityLayer() throws Exception {
        String token = jwtService.generateToken(user(Role.BUYER));

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", AUCTION_ID)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1500.00}"))
            .andExpect(status().isCreated());
    }

    @Test
    void placeBid_withSellerToken_shouldReturn403() throws Exception {
        String token = jwtService.generateToken(user(Role.SELLER));

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", AUCTION_ID)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1500.00}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void malformedToken_shouldReturn401Not500() throws Exception {
        mockMvc.perform(post("/api/auctions")
                .header("Authorization", "Bearer malformed.token.value")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validAuctionCreateRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(unauthenticated());
    }

    @Test
    void tokenWithInvalidRoleClaim_shouldReturn401Not500() throws Exception {
        String token = Jwts.builder()
            .setSubject(UUID.fromString("33333333-3333-3333-3333-333333333333").toString())
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("email", "seller@bidmart.test")
            .claim("role", "INVALID_ROLE")
            .signWith(
                Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256
            )
            .compact();

        mockMvc.perform(post("/api/auctions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validAuctionCreateRequest()))
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

    private String validAuctionCreateRequest() {
        return """
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
    }

    private AuctionDetailResponse dummyAuctionDetailResponse() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new AuctionDetailResponse(
            AUCTION_ID,
            UUID.fromString("55555555-5555-5555-5555-555555555555"),
            "Vintage Camera",
            "Working condition",
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "seller@bidmart.test",
            new BigDecimal("1000.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("100.00"),
            AuctionStatus.DRAFT,
            now,
            now.plusSeconds(60),
            now.plusSeconds(3660),
            null,
            60L,
            0,
            0L,
            new BigDecimal("1100.00"),
            false,
            true,
            null,
            null,
            List.of()
        );
    }
}
