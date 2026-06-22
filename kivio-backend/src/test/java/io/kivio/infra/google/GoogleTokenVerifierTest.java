package io.kivio.infra.google;

import io.kivio.domain.identity.exception.GoogleTokenInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * {@link GoogleTokenVerifier} の検証ロジックを単体検証します。
 *
 * <p>JWKS 取得・署名検証（ネットワーク I/O）は {@link NimbusJwtDecoder} に閉じているため、
 * デコーダをモックに差し替え、検証済み {@link Jwt} を与えて
 * audience / issuer / 必須クレームの各分岐を網羅する。
 */
@ExtendWith(MockitoExtension.class)
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";
    private static final String TOKEN = "google-id-token";

    @Mock
    private NimbusJwtDecoder jwtDecoder;

    private GoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new GoogleTokenVerifier(CLIENT_ID);
        // コンストラクタ内で生成される実デコーダ（JWKS URI）をモックに差し替える
        ReflectionTestUtils.setField(verifier, "jwtDecoder", jwtDecoder);
    }

    private static Jwt.Builder validJwtBuilder() {
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .audience(List.of(CLIENT_ID))
                .issuer("https://accounts.google.com")
                .subject("google-sub-123")
                .claim("email", "user@example.com");
    }

    @Test
    void should_return_user_info_when_token_is_valid() {
        Jwt jwt = validJwtBuilder().claim("name", "Test User").build();
        given(jwtDecoder.decode(TOKEN)).willReturn(jwt);

        GoogleUserInfo info = verifier.verify(TOKEN);

        assertThat(info.subject()).isEqualTo("google-sub-123");
        assertThat(info.email()).isEqualTo("user@example.com");
        assertThat(info.name()).isEqualTo("Test User");
    }

    @Test
    void should_allow_null_name_when_profile_scope_absent() {
        // name クレームなし（profile スコープ未付与）でも検証は成功する
        Jwt jwt = validJwtBuilder().build();
        given(jwtDecoder.decode(TOKEN)).willReturn(jwt);

        GoogleUserInfo info = verifier.verify(TOKEN);

        assertThat(info.name()).isNull();
    }

    @Test
    void should_throw_when_audience_does_not_contain_client_id() {
        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .audience(List.of("other-client.apps.googleusercontent.com"))
                .issuer("https://accounts.google.com")
                .subject("google-sub-123")
                .claim("email", "user@example.com")
                .build();
        given(jwtDecoder.decode(TOKEN)).willReturn(jwt);

        assertThatThrownBy(() -> verifier.verify(TOKEN))
                .isInstanceOf(GoogleTokenInvalidException.class);
    }

    @Test
    void should_throw_when_issuer_is_not_google() {
        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .audience(List.of(CLIENT_ID))
                .issuer("https://evil.example.com")
                .subject("google-sub-123")
                .claim("email", "user@example.com")
                .build();
        given(jwtDecoder.decode(TOKEN)).willReturn(jwt);

        assertThatThrownBy(() -> verifier.verify(TOKEN))
                .isInstanceOf(GoogleTokenInvalidException.class);
    }

    @Test
    void should_throw_when_subject_is_missing() {
        // aud / iss は妥当だが sub が欠落
        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .audience(List.of(CLIENT_ID))
                .issuer("https://accounts.google.com")
                .claim("email", "user@example.com")
                .build();
        given(jwtDecoder.decode(TOKEN)).willReturn(jwt);

        assertThatThrownBy(() -> verifier.verify(TOKEN))
                .isInstanceOf(GoogleTokenInvalidException.class);
    }

    @Test
    void should_throw_when_email_is_missing() {
        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .audience(List.of(CLIENT_ID))
                .issuer("https://accounts.google.com")
                .subject("google-sub-123")
                .build();
        given(jwtDecoder.decode(TOKEN)).willReturn(jwt);

        assertThatThrownBy(() -> verifier.verify(TOKEN))
                .isInstanceOf(GoogleTokenInvalidException.class);
    }

    @Test
    void should_translate_decoder_jwt_exception_to_domain_exception() {
        // 署名不正・期限切れ等で NimbusJwtDecoder が JwtException を投げるケース
        given(jwtDecoder.decode(TOKEN)).willThrow(new JwtException("invalid signature"));

        assertThatThrownBy(() -> verifier.verify(TOKEN))
                .isInstanceOf(GoogleTokenInvalidException.class);
    }
}
