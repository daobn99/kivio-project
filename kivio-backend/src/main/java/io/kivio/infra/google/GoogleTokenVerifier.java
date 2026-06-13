package io.kivio.infra.google;

import io.kivio.domain.identity.exception.GoogleTokenInvalidException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Google ID Tokenを検証し、ユーザー情報を取得するinfra層コンポーネントを表現します。
 * Spring SecurityのNimbusJwtDecoderを使用し、Googleの公開鍵でJWT署名を検証します。
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> VALID_ISSUERS = List.of(
            "accounts.google.com",
            "https://accounts.google.com");

    private final NimbusJwtDecoder jwtDecoder;
    private final String googleClientId;

    public GoogleTokenVerifier(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId) {
        this.googleClientId = googleClientId;
        this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
    }

    /**
     * Google ID Tokenを検証してユーザー情報を返します。
     *
     * @param idToken Google IDトークン（フロントエンドが取得したもの）
     * @return 検証済みのGoogleユーザー情報
     * @throws GoogleTokenInvalidException 検証失敗時
     */
    public GoogleUserInfo verify(String idToken) {
        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            validateAudience(jwt);
            validateIssuer(jwt);
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            if (subject == null || email == null) {
                throw new GoogleTokenInvalidException();
            }
            // name は profile スコープ未付与時に欠落しうる。必須ではないため null 許容で取得する
            String name = jwt.getClaimAsString("name");
            return new GoogleUserInfo(subject, email, name);
        } catch (GoogleTokenInvalidException e) {
            throw e;
        } catch (JwtException e) {
            log.warn("google_token_verification_failed reason={}", e.getMessage());
            throw new GoogleTokenInvalidException();
        }
    }

    /**
     * Google ID TokenのAudience（aud）が正しいクライアントIDを含んでいるか検証します。
     */
    private void validateAudience(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(googleClientId)) {
            throw new GoogleTokenInvalidException();
        }
    }

    /**
     * Google ID Tokenの発行元（issuer）が有効なものか検証します。
     */
    private void validateIssuer(Jwt jwt) {
        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
        if (issuer == null || !VALID_ISSUERS.contains(issuer)) {
            throw new GoogleTokenInvalidException();
        }
    }
}
