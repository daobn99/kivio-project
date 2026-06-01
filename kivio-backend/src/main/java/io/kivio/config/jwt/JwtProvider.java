package io.kivio.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.kivio.common.exception.TokenExpiredException;
import io.kivio.common.exception.TokenInvalidException;
import io.kivio.config.security.KivioUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;

    public JwtProvider(JwtProperties props) {
        byte[] keyBytes = Base64.getDecoder().decode(props.secret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiration = props.accessTokenExpiration();
    }

    public String generateAccessToken(UUID userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpiration)))
                .signWith(key)
                .compact();
    }

    /**
     * トークンを検証して Authentication を返す。無効な場合は empty を返す（例外はスローしない）。
     * filter → toAuthentication の二重パースを避けるため、isValid チェックも兼ねる。
     */
    public Optional<Authentication> toAuthentication(String token) {
        try {
            Claims claims = parseAndValidate(token);
            String subject = claims.getSubject();
            String role = claims.get("role", String.class);
            if (subject == null || role == null) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(subject);
            KivioUserDetails userDetails = new KivioUserDetails(userId, role);
            return Optional.of(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        } catch (TokenExpiredException | TokenInvalidException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * @throws TokenExpiredException アクセストークンの有効期限が切れている場合
     * @throws TokenInvalidException トークンが不正またはシグネチャが不一致の場合
     */
    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidException();
        }
    }

    public boolean isValid(String token) {
        return toAuthentication(token).isPresent();
    }
}
