package io.kivio.domain.identity.controller;

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
import io.kivio.domain.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 認証 API を表現します。
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
     * メールアドレスとパスワードでユーザーを登録します。
     */
    @PostMapping("/register")
    @Operation(summary = "メールアドレス・パスワード登録")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        URI location = URI.create("/api/v1/users/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * メールアドレス確認トークンを検証し、ユーザーを有効化します。
     */
    @PostMapping("/verify-email")
    @Operation(summary = "メールアドレス確認")
    public ResponseEntity<AuthTokenResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request));
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
