package io.kivio.domain.identity.controller;

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
import io.kivio.domain.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証 API を表現します。
 *
 * <p>新規登録は OTP（メール認証コード）+ Redis 一時ストレージによる 3 ステップ方式です。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "認証管理")
public class AuthController {

    private final AuthService authService;

    /**
     * メールアドレスの登録可否を確認します。
     */
    @PostMapping("/check-email")
    @Operation(summary = "メールアドレス重複チェック")
    public ResponseEntity<CheckEmailResponse> checkEmail(@Valid @RequestBody CheckEmailRequest request) {
        return ResponseEntity.ok(authService.checkEmail(request));
    }

    /**
     * 認証コード（OTP）をメール送信します（登録ステップ1）。
     */
    @PostMapping("/register/request-otp")
    @Operation(summary = "認証コード(OTP)送信")
    public ResponseEntity<RequestOtpResponse> requestOtp(@Valid @RequestBody RequestOtpRequest request) {
        return ResponseEntity.accepted().body(authService.requestOtp(request));
    }

    /**
     * 認証コード（OTP）を検証して登録セッションを発行します（登録ステップ2）。
     */
    @PostMapping("/register/verify-otp")
    @Operation(summary = "認証コード(OTP)検証")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    /**
     * パスワードを設定して登録を完了し、自動ログイン用のトークンを発行します（登録ステップ3）。
     */
    @PostMapping("/register/complete")
    @Operation(summary = "パスワード設定・登録完了・自動ログイン")
    public ResponseEntity<AuthTokenResponse> completeRegistration(
            @Valid @RequestBody CompleteRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.completeRegistration(request));
    }

    /**
     * メールアドレス・パスワードでログインし、アクセストークンとリフレッシュトークンを発行します。
     */
    @PostMapping("/login")
    @Operation(summary = "メールアドレス・パスワードログイン")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Google OAuth トークンを検証し、ユーザーを登録・ログインします。
     */
    @PostMapping("/google")
    @Operation(summary = "Google OAuth ログイン")
    public ResponseEntity<AuthTokenResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.googleLogin(request));
    }

    /**
     * リフレッシュトークンを検証し、新しいアクセストークンとリフレッシュトークンを発行します。
     */
    @PostMapping("/refresh")
    @Operation(summary = "アクセストークン再発行（Token Rotation）")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /**
     * リフレッシュトークンを無効化してログアウトします。
     */
    @PostMapping("/logout")
    @Operation(summary = "ログアウト（Refresh Token 無効化）")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
