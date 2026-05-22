package id.ac.ui.cs.advprog.biddingcommand.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import id.ac.ui.cs.advprog.biddingcommand.model.Role;
import id.ac.ui.cs.advprog.biddingcommand.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String PRIMARY_SIGNING_KEY = "12345678901234567890123456789012";
    private static final String SECONDARY_SIGNING_KEY = "abcdefghijklmnopqrstuvwxyz123456";

    @Test
    void generateTokenShouldCreateTokenWithUserIdEmailAndRole() {
        JwtService jwtService = new JwtService(PRIMARY_SIGNING_KEY, 3600);
        User user = createUser();

        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertEquals(user.getId().toString(), jwtService.extractUserId(token));
        assertEquals(user.getEmail(), jwtService.extractEmail(token));
        assertEquals(user.getRole().name(), jwtService.extractRole(token));
    }

    @Test
    void isValidShouldReturnTrueForValidToken() {
        JwtService jwtService = new JwtService(PRIMARY_SIGNING_KEY, 3600);

        String token = jwtService.generateToken(createUser());

        assertTrue(jwtService.isValid(token));
    }

    @Test
    void isValidShouldReturnFalseForMalformedToken() {
        JwtService jwtService = new JwtService(PRIMARY_SIGNING_KEY, 3600);

        assertFalse(jwtService.isValid("not-a-jwt"));
    }

    @Test
    void isValidShouldReturnFalseForTokenWithInvalidSignature() {
        JwtService signingService = new JwtService(SECONDARY_SIGNING_KEY, 3600);
        JwtService validatingService = new JwtService(PRIMARY_SIGNING_KEY, 3600);

        String token = signingService.generateToken(createUser());

        assertFalse(validatingService.isValid(token));
    }

    @Test
    void isValidShouldReturnFalseForExpiredToken() {
        JwtService jwtService = new JwtService(PRIMARY_SIGNING_KEY, -1);

        String token = jwtService.generateToken(createUser());

        assertFalse(jwtService.isValid(token));
    }

    @Test
    void constructorShouldRejectSecretShorterThan32Characters() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new JwtService("short-secret", 3600)
        );

        assertEquals("JWT secret must be at least 32 characters", exception.getMessage());
        assertFalse(exception.getMessage().contains("short-secret"));
    }

    @Test
    void extractMethodsShouldReturnExpectedClaimsForValidToken() {
        JwtService jwtService = new JwtService(PRIMARY_SIGNING_KEY, 3600);
        User user = createUser();
        String token = Jwts.builder()
            .setSubject(user.getId().toString())
            .setIssuedAt(Date.from(Instant.now()))
            .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .signWith(
                Keys.hmacShaKeyFor(PRIMARY_SIGNING_KEY.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256
            )
            .compact();

        assertEquals(user.getId().toString(), jwtService.extractUserId(token));
        assertEquals(user.getEmail(), jwtService.extractEmail(token));
        assertEquals(user.getRole().name(), jwtService.extractRole(token));
    }

    private User createUser() {
        return User.builder()
            .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .email("buyer@bidmart.test")
            .passwordHash("hashed-password")
            .role(Role.BUYER)
            .availableBalance(BigDecimal.ZERO)
            .heldBalance(BigDecimal.ZERO)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();
    }
}
