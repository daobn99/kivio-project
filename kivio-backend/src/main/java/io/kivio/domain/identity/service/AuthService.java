package io.kivio.domain.identity.service;

import io.kivio.config.jwt.JwtProperties;
import io.kivio.config.jwt.JwtProvider;
import io.kivio.domain.audit.annotation.Auditable;
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
import io.kivio.domain.identity.exception.InvalidCredentialsException;
import io.kivio.domain.identity.exception.RefreshTokenInvalidException;
import io.kivio.domain.identity.exception.UserDeactivatedException;
import io.kivio.domain.identity.repository.RefreshTokenRepository;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.infra.google.GoogleTokenVerifier;
import io.kivio.infra.google.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 認証サービスを表現します。
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationService emailVerificationService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    /**
     * メールアドレスの登録可否を確認します。
     */
    @Transactional(readOnly = true)
    public CheckEmailResponse checkEmail(CheckEmailRequest request) {
        boolean available = !userRepository.existsByEmail(request.getEmail());
        return new CheckEmailResponse(available);
    }

    /**
     * メールアドレスとパスワードでユーザーを登録します。
     *
     * @throws EmailAlreadyRegisteredException 既にメールアドレスが登録されている場合
     */
    @Auditable(action = "USER_REGISTERED", entityType = "USER")
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        User saved = userRepository.save(user);
        emailVerificationService.createAndSendVerificationToken(saved);
        log.info("user_registered userId={}", saved.getId());
        return RegisterResponse.from(saved);
    }

    /**
     * メール確認トークンを検証してアクセストークンを発行します。
     */
    @Auditable(action = "USER_EMAIL_VERIFIED", entityType = "USER")
    public AuthTokenResponse verifyEmail(VerifyEmailRequest request) {
        EmailVerificationToken tokenEntity = emailVerificationService.validateAndConsume(request.getToken());
        User user = userRepository.findByIdOrThrow(tokenEntity.getUserId());
        user.verifyEmail();
        userRepository.save(user);
        log.info("email_verified userId={}", user.getId());
        return generateTokenPair(user);
    }

    /**
     * メールアドレスとパスワードでログインします。
     *
     * @throws InvalidCredentialsException 認証情報が不正の場合（メール・パスワード不一致を区別しない）
     * @throws EmailNotVerifiedException   メールアドレス未確認の場合
     * @throws UserDeactivatedException    アカウントが無効化されている場合
     */
    @Auditable(action = "USER_LOGGED_IN", entityType = "USER")
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        // メール存在有無を攻撃者に漏らさないために、パスワード不一致も同じ例外を返す
        if (!user.hasPassword() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        if (!user.isActive()) {
            throw new UserDeactivatedException();
        }

        log.info("user_logged_in userId={}", user.getId());
        return generateTokenPair(user);
    }

    /**
     * Google ID Token を検証してログインします。同メールの既存アカウントがある場合は google_id を紐づけます。
     *
     * @throws UserDeactivatedException アカウントが無効化されている場合
     */
    @Auditable(action = "USER_LOGGED_IN", entityType = "USER")
    public AuthTokenResponse googleLogin(GoogleLoginRequest request) {
        GoogleUserInfo googleInfo = googleTokenVerifier.verify(request.getIdToken());
        User user = resolveGoogleUser(googleInfo);

        if (!user.isActive()) {
            throw new UserDeactivatedException();
        }

        log.info("user_google_logged_in userId={}", user.getId());
        return generateTokenPair(user);
    }

    /**
     * Refresh Token を検証して Token Rotation を行い新しいトークンペアを返します。
     *
     * <p>
     * 既に失効したトークンが使われた場合（Reuse Detection）、
     * そのユーザーの全セッションを無効化してセキュリティインシデントに対応します。
     *
     * @throws RefreshTokenInvalidException トークンが無効・失効・期限切れの場合
     */
    public AuthTokenResponse refresh(RefreshRequest request) {
        String tokenHash = TokenHashUtils.sha256Hex(request.getRefreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(RefreshTokenInvalidException::new);

        // 失効済みトークンの再利用を検知したら全セッションを無効化する（トークン盗用の可能性）
        if (refreshToken.isRevoked()) {
            refreshTokenRepository.deleteAllByUserId(refreshToken.getUserId());
            log.warn("refresh_token_reuse_detected userId={}", refreshToken.getUserId());
            throw new RefreshTokenInvalidException();
        }

        if (refreshToken.isExpired()) {
            throw new RefreshTokenInvalidException();
        }

        User user = userRepository.findByIdOrThrow(refreshToken.getUserId());

        // Token Rotation: 旧トークンを失効済みにマークして新しいトークンペアを発行する
        // 物理削除せず revoked = true で保持することで次回の Reuse Detection を有効にする
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);
        return generateTokenPair(user);
    }

    /**
     * ログアウトしてリフレッシュトークンを無効化します。
     */
    @Auditable(action = "USER_LOGGED_OUT", entityType = "USER")
    public void logout(LogoutRequest request) {
        String tokenHash = TokenHashUtils.sha256Hex(request.getRefreshToken());
        refreshTokenRepository.deleteByTokenHash(tokenHash);
        log.info("user_logged_out");
    }

    /**
     * Google ID Token の情報からユーザーを解決します。
     */
    private User resolveGoogleUser(GoogleUserInfo googleInfo) {
        return userRepository.findByGoogleId(googleInfo.subject())
                .or(() -> linkGoogleIdToExistingAccount(googleInfo))
                .orElseGet(() -> createGoogleUser(googleInfo));
    }

    /**
     * 同メールアドレスの既存ユーザーがいる場合、そのユーザーに Google ID を紐づけて返します。
     */
    private Optional<User> linkGoogleIdToExistingAccount(GoogleUserInfo googleInfo) {
        return userRepository.findByEmail(googleInfo.email())
                .map(existingUser -> {
                    existingUser.linkGoogleId(googleInfo.subject());
                    // Google がメールアドレスを確認済みのため、未確認ユーザーでも確認済みにする
                    existingUser.verifyEmail();
                    return userRepository.save(existingUser);
                });
    }

    /**
     * 同メールアドレスのユーザーがいない場合、新規にユーザーを作成して返します。
     */
    private User createGoogleUser(GoogleUserInfo googleInfo) {
        User newUser = User.builder()
                .email(googleInfo.email())
                .googleId(googleInfo.subject())
                .build();
        // Google はメールアドレスを検証済みのため、確認済みとして扱う
        newUser.verifyEmail();
        return userRepository.save(newUser);
    }

    /**
     * ユーザー情報からアクセストークンとリフレッシュトークンを発行します。
     */
    private AuthTokenResponse generateTokenPair(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getRole());
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = TokenHashUtils.sha256Hex(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(jwtProperties.refreshTokenExpiration()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthTokenResponse.of(accessToken, rawRefreshToken, jwtProperties.accessTokenExpiration());
    }
}
