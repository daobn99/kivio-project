package io.kivio.domain.identity.service;

import io.kivio.config.AuthProperties;
import io.kivio.config.jwt.JwtProperties;
import io.kivio.config.jwt.JwtProvider;
import io.kivio.domain.audit.annotation.Auditable;
import io.kivio.domain.identity.domain.RefreshToken;
import io.kivio.domain.identity.domain.User;
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
import io.kivio.domain.identity.exception.InvalidCredentialsException;
import io.kivio.domain.identity.exception.RefreshTokenInvalidException;
import io.kivio.domain.identity.exception.UserDeactivatedException;
import io.kivio.domain.identity.repository.RefreshTokenRepository;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.infra.google.GoogleTokenVerifier;
import io.kivio.infra.google.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 認証サービスを表現します。
 *
 * <p>新規登録は OTP（メール認証コード）+ Redis 一時ストレージによる 3 ステップ方式です。
 * {@code users} レコードはメール認証（OTP 検証）完了後の登録完了時にのみ作成します。
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final RegistrationSessionService registrationSessionService;
    private final AuthEmailService authEmailService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;

    /**
     * メールアドレスの登録可否を確認します。
     */
    @Transactional(readOnly = true)
    public CheckEmailResponse checkEmail(CheckEmailRequest request) {
        boolean available = !userRepository.existsByEmail(request.email());
        return new CheckEmailResponse(available);
    }

    /**
     * 認証コード（OTP）を生成してメール送信します（登録ステップ1）。
     *
     * <p>この時点では {@code users} レコードは作成しません。OTP は Redis に TTL 付きで保存します。
     *
     * @throws EmailAlreadyRegisteredException 既にメールアドレスが登録されている場合
     */
    @Transactional(readOnly = true)
    public RequestOtpResponse requestOtp(RequestOtpRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }

        String otp = otpService.issue(request.email());
        authEmailService.sendRegistrationOtp(request.email(), otp);
        log.info("registration_otp_requested");

        return RequestOtpResponse.of(authProperties.otp().ttl().toSeconds());
    }

    /**
     * 認証コード（OTP）を検証して登録セッション（registrationToken）を発行します（登録ステップ2）。
     *
     * <p>DB は参照しません（OTP・登録セッションは Redis のみ）。
     */
    @Transactional(readOnly = true)
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        otpService.verify(request.email(), request.otp());
        String registrationToken = registrationSessionService.create(request.email());
        log.info("registration_otp_verified");
        return VerifyOtpResponse.of(
                registrationToken, authProperties.registrationSession().ttl().toSeconds());
    }

    /**
     * パスワードを設定してユーザーを作成し、自動ログイン用のトークンを発行します（登録ステップ3）。
     *
     * <p>登録セッションから認証済みメールアドレスを取得し、{@code users} を新規作成します。
     *
     * @throws io.kivio.domain.identity.exception.RegistrationSessionInvalidException 登録セッションが無効・期限切れ・使用済みの場合
     * @throws EmailAlreadyRegisteredException 検証〜完了の間に同一メールが登録された場合
     */
    @Auditable(action = "USER_REGISTERED", entityType = "USER")
    public AuthTokenResponse completeRegistration(CompleteRegistrationRequest request) {
        String email = registrationSessionService.consume(request.registrationToken());

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .build();
        try {
            // saveAndFlush で即時フラッシュし、並行リクエストの一意制約違反をここで捕捉する
            User saved = userRepository.saveAndFlush(user);
            log.info("user_registered userId={}", saved.getId());
            return generateTokenPair(saved);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    /**
     * メールアドレスとパスワードでログインします。
     *
     * <p>{@code users} には認証済みユーザーのみが存在するため、メール確認状態のチェックは行いません。
     *
     * @throws InvalidCredentialsException 認証情報が不正の場合（メール・パスワード不一致を区別しない）
     * @throws UserDeactivatedException    アカウントが無効化されている場合
     */
    @Auditable(action = "USER_LOGGED_IN", entityType = "USER")
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        // メール存在有無を攻撃者に漏らさないために、パスワード不一致も同じ例外を返す
        if (!user.hasPassword() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
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
        GoogleUserInfo googleInfo = googleTokenVerifier.verify(request.idToken());
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
        String tokenHash = TokenHashUtils.sha256Hex(request.refreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(RefreshTokenInvalidException::new);

        // 失効済みトークンの再利用を検知したら全セッションを無効化する（トークン盗用の可能性）
        if (refreshToken.isRevoked()) {
            refreshTokenRepository.deleteAllByUserId(refreshToken.getUserId());
            log.warn("refresh_token_reuse_detected userId={}", refreshToken.getUserId());
            throw new RefreshTokenInvalidException();
        }

        if (refreshToken.isExpired()) {
            // 期限切れトークンは即時削除して DB の肥大化を防ぐ
            refreshTokenRepository.deleteByTokenHash(tokenHash);
            throw new RefreshTokenInvalidException();
        }

        User user = userRepository.findByIdOrThrow(refreshToken.getUserId());

        if (!user.isActive()) {
            throw new UserDeactivatedException();
        }

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
        String tokenHash = TokenHashUtils.sha256Hex(request.refreshToken());
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
                    return userRepository.save(existingUser);
                });
    }

    /**
     * 同メールアドレスのユーザーがいない場合、新規にユーザーを作成して返します。
     *
     * <p>Google はメールアドレスを検証済みのため、確認フラグを持たずにそのまま作成します。
     */
    private User createGoogleUser(GoogleUserInfo googleInfo) {
        User newUser = User.builder()
                .email(googleInfo.email())
                .googleId(googleInfo.subject())
                .displayName(resolveGoogleDisplayName(googleInfo))
                .build();
        return userRepository.save(newUser);
    }

    /**
     * Google プロフィールから表示名を決定します。
     *
     * <p>{@code name} クレームが無い（profile スコープ未付与）場合はメールアドレスの
     * ローカル部をフォールバックに用い、空の表示名を作らないようにします。
     * 表示名は {@code VARCHAR(100)} のため 100 文字に切り詰めます。
     */
    private String resolveGoogleDisplayName(GoogleUserInfo googleInfo) {
        String name = googleInfo.name();
        String resolved = (name != null && !name.isBlank())
                ? name.trim()
                : googleInfo.email().split("@", 2)[0];
        return resolved.length() > 100 ? resolved.substring(0, 100) : resolved;
    }

    /**
     * ユーザー情報からアクセストークンとリフレッシュトークンを発行します。
     */
    private AuthTokenResponse generateTokenPair(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getRole().name());
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
