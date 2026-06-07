package io.kivio.domain.identity.service;

import io.kivio.config.jwt.JwtProperties;
import io.kivio.config.jwt.JwtProvider;
import io.kivio.domain.identity.domain.EmailVerificationToken;
import io.kivio.domain.identity.domain.RefreshToken;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.dto.request.CheckEmailRequest;
import io.kivio.domain.identity.dto.request.GoogleLoginRequest;
import io.kivio.domain.identity.dto.request.LoginRequest;
import io.kivio.domain.identity.dto.request.LogoutRequest;
import io.kivio.domain.identity.dto.request.RefreshRequest;
import io.kivio.domain.identity.dto.request.RegisterRequest;
import io.kivio.domain.identity.dto.request.VerifyEmailRequest;
import io.kivio.domain.identity.dto.response.AuthTokenResponse;
import io.kivio.domain.identity.dto.response.CheckEmailResponse;
import io.kivio.domain.identity.dto.response.RegisterResponse;
import io.kivio.domain.identity.exception.EmailAlreadyRegisteredException;
import io.kivio.domain.identity.exception.EmailNotVerifiedException;
import io.kivio.domain.identity.exception.EmailVerificationTokenExpiredException;
import io.kivio.domain.identity.exception.EmailVerificationTokenInvalidException;
import io.kivio.domain.identity.exception.GoogleTokenInvalidException;
import io.kivio.domain.identity.exception.InvalidCredentialsException;
import io.kivio.domain.identity.exception.RefreshTokenInvalidException;
import io.kivio.domain.identity.exception.UserDeactivatedException;
import io.kivio.domain.identity.repository.RefreshTokenRepository;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.infra.google.GoogleTokenVerifier;
import io.kivio.infra.google.GoogleUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private JwtProvider jwtProvider;
    @Mock private PasswordEncoder passwordEncoder;

    // JwtProperties はレコードのためリアルインスタンスを使用する
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "dGVzdC1vbmx5LXNlY3JldC1yZXBsYWNlLWluLXByb2QtIQ==", 900L, 604800L);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, emailVerificationService,
                googleTokenVerifier, jwtProvider, JWT_PROPERTIES, passwordEncoder);
    }

    // ============================================================
    // checkEmail
    // ============================================================

    @Test
    void should_return_available_true_when_email_is_not_registered() {
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);

        CheckEmailResponse response = authService.checkEmail(
                new CheckEmailRequest("new@example.com"));

        assertThat(response.available()).isTrue();
    }

    @Test
    void should_return_available_false_when_email_is_already_registered() {
        given(userRepository.existsByEmail("taken@example.com")).willReturn(true);

        CheckEmailResponse response = authService.checkEmail(
                new CheckEmailRequest("taken@example.com"));

        assertThat(response.available()).isFalse();
    }

    // ============================================================
    // register
    // ============================================================

    @Test
    void should_save_user_and_send_verification_email_when_register_succeeds() {
        UUID userId = UUID.randomUUID();
        User saved = buildUser(userId, "new@example.com", "hashed", true, "ACTIVE");
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willReturn(saved);

        RegisterResponse response = authService.register(
                new RegisterRequest("new@example.com", "Password123!", "Password123!"));

        assertThat(response.email()).isEqualTo("new@example.com");
        then(passwordEncoder).should().encode("Password123!");
        then(emailVerificationService).should().createAndSendVerificationToken(saved);
    }

    @Test
    void should_throw_EmailAlreadyRegisteredException_when_email_is_duplicate() {
        given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("dup@example.com", "Password123!", "Password123!")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        then(userRepository).should(never()).save(any());
    }

    // ============================================================
    // verifyEmail
    // ============================================================

    @Test
    void should_return_tokens_and_verify_user_when_token_is_valid() {
        UUID userId = UUID.randomUUID();
        EmailVerificationToken tokenEntity = buildVerificationToken(userId, false);
        User user = buildUser(userId, "user@example.com", "hashed", false, "ACTIVE");
        given(emailVerificationService.validateAndConsume("raw-token")).willReturn(tokenEntity);
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);
        stubJwtPair();

        AuthTokenResponse response = authService.verifyEmail(
                new VerifyEmailRequest("raw-token"));

        assertThat(response.accessToken()).isEqualTo("test-access-token");
        assertThat(response.refreshToken()).isNotBlank();
        then(userRepository).should().save(user);
    }

    @Test
    void should_propagate_EmailVerificationTokenInvalidException_when_token_is_not_found() {
        given(emailVerificationService.validateAndConsume(any()))
                .willThrow(EmailVerificationTokenInvalidException.class);

        assertThatThrownBy(() -> authService.verifyEmail(
                new VerifyEmailRequest("bad-token")))
                .isInstanceOf(EmailVerificationTokenInvalidException.class);
    }

    @Test
    void should_propagate_EmailVerificationTokenExpiredException_when_token_is_expired() {
        given(emailVerificationService.validateAndConsume(any()))
                .willThrow(EmailVerificationTokenExpiredException.class);

        assertThatThrownBy(() -> authService.verifyEmail(
                new VerifyEmailRequest("expired-token")))
                .isInstanceOf(EmailVerificationTokenExpiredException.class);
    }

    // ============================================================
    // login
    // ============================================================

    @Test
    void should_return_tokens_when_login_with_valid_credentials() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "user@example.com", "hashed", true, "ACTIVE");
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Password123!", "hashed")).willReturn(true);
        stubJwtPair();

        AuthTokenResponse response = authService.login(
                new LoginRequest("user@example.com", "Password123!"));

        assertThat(response.accessToken()).isEqualTo("test-access-token");
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void should_throw_InvalidCredentialsException_when_password_does_not_match() {
        User user = buildUser(UUID.randomUUID(), "user@example.com", "hashed", true, "ACTIVE");
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("user@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void should_throw_InvalidCredentialsException_when_email_does_not_exist() {
        given(userRepository.findByEmail("ghost@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("ghost@example.com", "Password123!")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void should_throw_EmailNotVerifiedException_when_email_is_not_verified() {
        // emailVerified = false
        User user = buildUser(UUID.randomUUID(), "user@example.com", "hashed", false, "ACTIVE");
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Password123!", "hashed")).willReturn(true);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("user@example.com", "Password123!")))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void should_throw_UserDeactivatedException_when_account_is_deactivated() {
        // status = SUSPENDED (非ACTIVE)
        User user = buildUser(UUID.randomUUID(), "user@example.com", "hashed", true, "SUSPENDED");
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Password123!", "hashed")).willReturn(true);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("user@example.com", "Password123!")))
                .isInstanceOf(UserDeactivatedException.class);
    }

    // ============================================================
    // googleLogin
    // ============================================================

    @Test
    void should_create_new_user_and_return_tokens_when_google_user_has_no_existing_account() {
        GoogleUserInfo info = new GoogleUserInfo("google-sub-001", "newgoogle@example.com");
        UUID newUserId = UUID.randomUUID();
        User created = buildUser(newUserId, "newgoogle@example.com", null, true, "ACTIVE");
        given(googleTokenVerifier.verify("id-token")).willReturn(info);
        given(userRepository.findByGoogleId("google-sub-001")).willReturn(Optional.empty());
        given(userRepository.findByEmail("newgoogle@example.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(created);
        stubJwtPair();

        AuthTokenResponse response = authService.googleLogin(
                new GoogleLoginRequest("id-token"));

        assertThat(response.accessToken()).isEqualTo("test-access-token");
    }

    @Test
    void should_link_google_id_to_existing_account_when_same_email_already_exists() {
        UUID userId = UUID.randomUUID();
        GoogleUserInfo info = new GoogleUserInfo("google-sub-002", "existing@example.com");
        User existing = buildUser(userId, "existing@example.com", "hashed", true, "ACTIVE");
        given(googleTokenVerifier.verify("id-token")).willReturn(info);
        given(userRepository.findByGoogleId("google-sub-002")).willReturn(Optional.empty());
        given(userRepository.findByEmail("existing@example.com")).willReturn(Optional.of(existing));
        given(userRepository.save(existing)).willReturn(existing);
        stubJwtPair();

        authService.googleLogin(new GoogleLoginRequest("id-token"));

        assertThat(existing.getGoogleId()).isEqualTo("google-sub-002");
    }

    @Test
    void should_throw_GoogleTokenInvalidException_when_id_token_is_invalid() {
        given(googleTokenVerifier.verify(any())).willThrow(GoogleTokenInvalidException.class);

        assertThatThrownBy(() -> authService.googleLogin(
                new GoogleLoginRequest("bad-token")))
                .isInstanceOf(GoogleTokenInvalidException.class);
    }

    @Test
    void should_throw_UserDeactivatedException_when_google_user_account_is_deactivated() {
        UUID userId = UUID.randomUUID();
        GoogleUserInfo info = new GoogleUserInfo("google-sub-003", "deactivated@example.com");
        User deactivated = buildUser(userId, "deactivated@example.com", null, true, "SUSPENDED");
        given(googleTokenVerifier.verify("id-token")).willReturn(info);
        given(userRepository.findByGoogleId("google-sub-003")).willReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> authService.googleLogin(
                new GoogleLoginRequest("id-token")))
                .isInstanceOf(UserDeactivatedException.class);
    }

    // ============================================================
    // refresh
    // ============================================================

    @Test
    void should_rotate_token_and_return_new_tokens_when_refresh_token_is_valid() {
        String rawToken = "valid-raw-refresh-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "user@example.com", "hashed", true, "ACTIVE");
        RefreshToken token = buildRefreshToken(userId, tokenHash, false, 3600L);
        given(refreshTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(token));
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);
        stubJwtPair();

        AuthTokenResponse response = authService.refresh(
                new RefreshRequest(rawToken));

        assertThat(response.accessToken()).isEqualTo("test-access-token");
        assertThat(response.refreshToken()).isNotBlank();
        // Token Rotation: 旧トークンが失効済みになっていること
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void should_throw_RefreshTokenInvalidException_when_token_is_not_found() {
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(
                new RefreshRequest("unknown-token")))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void should_throw_RefreshTokenInvalidException_when_token_is_expired() {
        String rawToken = "expired-raw-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        RefreshToken expired = buildRefreshToken(UUID.randomUUID(), tokenHash, false, -3600L);
        given(refreshTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(
                new RefreshRequest(rawToken)))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void should_invalidate_all_sessions_and_throw_when_revoked_token_is_reused() {
        String rawToken = "revoked-raw-token";
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        UUID userId = UUID.randomUUID();
        // revoked = true: 既に失効済み（Reuse Detection の対象）
        RefreshToken revoked = buildRefreshToken(userId, tokenHash, true, 3600L);
        given(refreshTokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(
                new RefreshRequest(rawToken)))
                .isInstanceOf(RefreshTokenInvalidException.class);

        then(refreshTokenRepository).should().deleteAllByUserId(userId);
    }

    // ============================================================
    // logout
    // ============================================================

    @Test
    void should_delete_refresh_token_when_logout() {
        String rawToken = "logout-token";
        String expectedHash = TokenHashUtils.sha256Hex(rawToken);

        authService.logout(new LogoutRequest(rawToken));

        then(refreshTokenRepository).should().deleteByTokenHash(expectedHash);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void stubJwtPair() {
        given(jwtProvider.generateAccessToken(any(UUID.class), anyString()))
                .willReturn("test-access-token");
    }

    private User buildUser(UUID id, String email, String passwordHash,
                           boolean emailVerified, String status) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash(passwordHash)
                .emailVerified(emailVerified)
                .status(status)
                .build();
    }

    private EmailVerificationToken buildVerificationToken(UUID userId, boolean expired) {
        return EmailVerificationToken.builder()
                .userId(userId)
                .tokenHash("token-hash")
                .expiresAt(expired
                        ? Instant.now().minusSeconds(3600)
                        : Instant.now().plusSeconds(3600))
                .build();
    }

    private RefreshToken buildRefreshToken(UUID userId, String tokenHash,
                                           boolean revoked, long expiresOffsetSeconds) {
        return RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(expiresOffsetSeconds))
                .revoked(revoked)
                .build();
    }
}
