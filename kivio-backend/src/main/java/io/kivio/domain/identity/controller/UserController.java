package io.kivio.domain.identity.controller;

import io.kivio.config.security.KivioUserDetails;
import io.kivio.domain.identity.dto.response.UserResponse;
import io.kivio.domain.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
}
