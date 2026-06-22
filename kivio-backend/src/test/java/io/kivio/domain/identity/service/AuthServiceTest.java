package io.kivio.domain.identity.service;

import io.kivio.config.AuthProperties;
import io.kivio.config.jwt.JwtProperties;
import io.kivio.config.jwt.JwtProvider;
import io.kivio.domain.identity.domain.RefreshToken;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.domain.UserStatus;
import io.kivio.domain.identity.dto.request.CheckEmailRequest;
import io.kivio.domain.identity.dto.request.CompleteRegistrationRequest;
import io.kivio.domain.identity.dto.request.GoogleLoginRequest;
import io.kivio.domain.identity.dto.request.LoginRequest;
import io.kivio.domain.identity.dto.request.LogoutRequest;
import io.kivio.domain.identity.dto.request.RefreshRequest;
import io.kivio.domain.identity.dto.request.RequestOtpRequest;
import io.kivio.domain.identity.dto.request.VerifyOtpRequest;
import io.kivio.domain.identity.dto.response.AuthTokenResponse;
import io.kivio.domain.identity.dto.response.CheckEmailResponse;
import io.kivio.domain.identity.dto.response.RequestOtpResponse;
import io.kivio.domain.identity.dto.response.VerifyOtpResponse;
import io.kivio.domain.identity.exception.EmailAlreadyRegisteredException;
import io.kivio.domain.identity.exception.GoogleTokenInvalidException;
import io.kivio.domain.identity.exception.InvalidCredentialsException;
import io.kivio.domain.identity.exception.OtpExpiredException;
import io.kivio.domain.identity.exception.OtpInvalidException;
import io.kivio.domain.identity.exception.OtpMaxAttemptsExceededException;
import io.kivio.domain.identity.exception.RefreshTokenInvalidException;
import io.kivio.domain.identity.exception.RegistrationSessionInvalidException;
import io.kivio.domain.identity.exception.UserDeactivatedException;
import io.kivio.domain.identity.repository.RefreshTokenRepository;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.infra.google.GoogleTokenVerifier;
import io.kivio.infra.google.GoogleUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
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
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private OtpService otpService;
    @Mock private RegistrationSessionService registrationSessionService;
    @Mock private AuthEmailService authEmailService;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private JwtProvider jwtProvider;
    @Mock private PasswordEncoder passwordEncoder;

    // レコードはモック不可のためリアルインスタンスを使用する
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "dGVzdC1vbmx5LXNlY3JldC1yZXBsYWNlLWluLXByb2QtIQ==", 900L, 604800L);
    private static final AuthProperties AUTH_PROPERTIES = new AuthProperties(
            new AuthProperties.Otp(6, Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5),
            new AuthProperties.RegistrationSession(Duration.ofMinutes(30)));

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, otpService, registrationSessionService,
                authEmailService, googleTokenVerifier, jwtProvider, JWT_PROPERTIES, AUTH_PROPERTIES,
                passwordEncoder);
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
    // requestOtp（登録ステップ1）
    // ============================================================

    @Test
    void should_issue_otp_and_send_email_when_request_otp_succeeds() {
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);
        given(otpService.issue("new@example.com")).willReturn("428170");

        RequestOtpResponse response = authService.requestOtp(
                new RequestOtpRequest("new@example.com"));

        assertThat(response.expiresInSeconds()).isEqualTo(600);
        then(otpService).should().issue("new@example.com");
        then(authEmailService).should().sendRegistrationOtp("new@example.com", "428170");
    }

    @Test
    void should_throw_EmailAlreadyRegisteredException_when_request_otp_for_duplicate_email() {
        given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.requestOtp(new RequestOtpRequest("dup@example.com")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        then(otpService).should(never()).issue(anyString());
        then(authEmailService).should(never()).sendRegistrationOtp(anyString(), anyString());
    }

    // ============================================================
    // verifyOtp（登録ステップ2）
    // ============================================================

    @Test
    void should_return_registration_token_when_otp_is_valid() {
        given(registrationSessionService.create("user@example.com")).willReturn("reg-token-uuid");

        VerifyOtpResponse response = authService.verifyOtp(
                new VerifyOtpRequest("user@example.com", "428170"));

        assertThat(response.registrationToken()).isEqualTo("reg-token-uuid");
        assertThat(response.expiresInSeconds()).isEqualTo(1800);
        then(otpService).should().verify("user@example.com", "428170");
    }

    @Test
    void should_propagate_OtpInvalidException_when_otp_does_not_match() {
        willThrow(new OtpInvalidException()).given(otpService).verify(anyString(), anyString());

        assertThatThrownBy(() -> authService.verifyOtp(
                new VerifyOtpRequest("user@example.com", "000000")))
                .isInstanceOf(OtpInvalidException.class);

        then(registrationSessionService).should(never()).create(anyString());
    }

    @Test
    void should_propagate_OtpExpiredException_when_otp_is_expired() {
        willThrow(new OtpExpiredException()).given(otpService).verify(anyString(), anyString());

        assertThatThrownBy(() -> authService.verifyOtp(
                new VerifyOtpRequest("user@example.com", "428170")))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void should_propagate_OtpMaxAttemptsExceededException_when_attempts_exhausted() {
        willThrow(new OtpMaxAttemptsExceededException()).given(otpService).verify(anyString(), anyString());

        assertThatThrownBy(() -> authService.verifyOtp(
                new VerifyOtpRequest("user@example.com", "428170")))
                .isInstanceOf(OtpMaxAttemptsExceededException.class);
    }

    // ============================================================
    // completeRegistration（登録ステップ3）
    // ============================================================

    @Test
    void should_create_user_and_return_tokens_when_complete_registration_succeeds() {
        UUID userId = UUID.randomUUID();
        User saved = buildUser(userId, "new@example.com", "hashed", UserStatus.ACTIVE);
        given(registrationSessionService.consume("reg-token")).willReturn("new@example.com");
        given(passwordEncoder.encode("Password123!")).willReturn("hashed");
        given(userRepository.saveAndFlush(any(User.class))).willReturn(saved);
        stubJwtPair();

        AuthTokenResponse response = authService.completeRegistration(
                new CompleteRegistrationRequest("reg-token", "Password123!", "Password123!", "Alice"));

        assertThat(response.accessToken()).isEqualTo("test-access-token");
        assertThat(response.refreshToken()).isNotBlank();
        then(passwordEncoder).should().encode("Password123!");
    }

    @Test
    void should_propagate_RegistrationSessionInvalidException_when_token_is_invalid() {
        willThrow(new RegistrationSessionInvalidException())
                .given(registrationSessionService).consume(anyString());

        assertThatThrownBy(() -> authService.completeRegistration(
                new CompleteRegistrationRequest("bad-token", "Password123!", "Password123!", "Alice")))
                .isInstanceOf(RegistrationSessionInvalidException.class);

        then(userRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void should_throw_EmailAlreadyRegisteredException_when_unique_violation_on_complete() {
        given(registrationSessionService.consume("reg-token")).willReturn("dup@example.com");
        given(passwordEncoder.encode(anyString())).willReturn("hashed");
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> authService.completeRegistration(
                new CompleteRegistrationRequest("reg-token", "Password123!", "Password123!", "Alice")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    // ============================================================
    // login
    // ============================================================

    @Test
    void should_return_tokens_when_login_with_valid_credentials() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "user@example.com", "hashed", UserStatus.ACTIVE);
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
        User user = buildUser(UUID.randomUUID(), "user@example.com", "hashed", UserStatus.ACTIVE);
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
    void should_throw_UserDeactivatedException_when_account_is_deactivated() {
        User user = buildUser(UUID.randomUUID(), "user@example.com", "hashed", UserStatus.INACTIVE);
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
        GoogleUserInfo info = new GoogleUserInfo("google-sub-001", "newgoogle@example.com", "New Google User");
        UUID newUserId = UUID.randomUUID();
        User created = buildUser(newUserId, "newgoogle@example.com", null, UserStatus.ACTIVE);
        given(googleTokenVerifier.verify("id-token")).willReturn(info);
        given(userRepository.findByGoogleId("google-sub-001")).willReturn(Optional.empty());
        given(userRepository.findByEmail("newgoogle@example.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(created);
        stubJwtPair();

        AuthTokenResponse response = authService.googleLogin(
                new GoogleLoginRequest("id-token"));

        assertThat(response.accessToken()).isEqualTo("test-access-token");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(userCaptor.capture());
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("New Google User");
    }

    @Test
    void should_fall_back_to_email_local_part_when_google_user_has_no_name() {
        GoogleUserInfo info = new GoogleUserInfo("google-sub-004", "noname@example.com", null);
        UUID newUserId = UUID.randomUUID();
        User created = buildUser(newUserId, "noname@example.com", null, UserStatus.ACTIVE);
        given(googleTokenVerifier.verify("id-token")).willReturn(info);
        given(userRepository.findByGoogleId("google-sub-004")).willReturn(Optional.empty());
        given(userRepository.findByEmail("noname@example.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(created);
        stubJwtPair();

        authService.googleLogin(new GoogleLoginRequest("id-token"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(userCaptor.capture());
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("noname");
    }

    @Test
    void should_link_google_id_to_existing_account_when_same_email_already_exists() {
        UUID userId = UUID.randomUUID();
        GoogleUserInfo info = new GoogleUserInfo("google-sub-002", "existing@example.com", "Existing User");
        User existing = buildUser(userId, "existing@example.com", "hashed", UserStatus.ACTIVE);
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
        GoogleUserInfo info = new GoogleUserInfo("google-sub-003", "deactivated@example.com", "Deactivated User");
        User deactivated = buildUser(userId, "deactivated@example.com", null, UserStatus.INACTIVE);
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
        User user = buildUser(userId, "user@example.com", "hashed", UserStatus.ACTIVE);
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

    private User buildUser(UUID id, String email, String passwordHash, UserStatus status) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash(passwordHash)
                .status(status)
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
