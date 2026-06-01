package io.kivio.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

public final class TestJwtTokenFactory {

    // application-test.yaml の app.jwt.secret と一致させること
    private static final String TEST_SECRET = "dGVzdC1vbmx5LXNlY3JldC1yZXBsYWNlLWluLXByb2QtIQ==";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET));
    private static final long EXPIRY_SECONDS = 900L;

    public static String generateToken(UUID userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(EXPIRY_SECONDS)))
                .signWith(KEY)
                .compact();
    }

    public static String buyerToken() {
        return generateToken(UUID.randomUUID(), "BUYER");
    }

    public static String buyerToken(UUID userId) {
        return generateToken(userId, "BUYER");
    }

    public static String sellerToken() {
        return generateToken(UUID.randomUUID(), "SELLER");
    }

    public static String sellerToken(UUID userId) {
        return generateToken(userId, "SELLER");
    }

    public static String adminToken() {
        return generateToken(UUID.randomUUID(), "ADMIN");
    }

    private TestJwtTokenFactory() {}
}
