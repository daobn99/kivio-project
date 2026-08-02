package io.kivio.domain.identity.controller;

import io.kivio.config.security.KivioUserDetails;
import io.kivio.domain.identity.dto.request.ChangePasswordRequest;
import io.kivio.domain.identity.dto.request.UpdateProfileRequest;
import io.kivio.domain.identity.dto.response.UserResponse;
import io.kivio.domain.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ユーザー API を表現します。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "ユーザー管理")
public class UserController {

    private final UserService userService;

    /**
     * 認証中ユーザー自身のプロフィールを取得します。
     *
     * <p>
     * アクセストークン（{@code Authorization: Bearer}）の {@code sub}（user_id）から
     * プロフィールを解決して返します。
     */
    @GetMapping("/me")
    @Operation(summary = "自分のプロフィール取得")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal KivioUserDetails principal) {
        return ResponseEntity.ok(userService.getById(principal.getUserId()));
    }

    /**
     * 認証中ユーザー自身のプロフィールを部分更新します。
     *
     * <p>
     * 送信されたフィールドのみ更新します（未送信は不変）。
     */
    @PatchMapping("/me")
    @Operation(summary = "プロフィール更新")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal KivioUserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(principal.getUserId(), request));
    }

    /**
     * 認証中ユーザー自身のパスワードを変更します。
     *
     * <p>
     * 現在のパスワードを照合し、一致した場合のみ再ハッシュして更新します。
     * 不一致 / Google ログイン専用ユーザーは {@code PASSWORD_CHANGE_FAILED}（400）。
     */
    @PatchMapping("/me/password")
    @Operation(summary = "パスワード変更")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal KivioUserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 認証中ユーザー自身のアカウントを退会（論理削除）します。
     */
    @DeleteMapping("/me")
    @Operation(summary = "退会（論理削除）")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal KivioUserDetails principal) {
        userService.withdraw(principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
