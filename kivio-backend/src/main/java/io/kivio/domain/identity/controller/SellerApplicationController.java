package io.kivio.domain.identity.controller;

import io.kivio.config.security.KivioUserDetails;
import io.kivio.domain.identity.dto.request.CreateSellerApplicationRequest;
import io.kivio.domain.identity.dto.response.SellerApplicationResponse;
import io.kivio.domain.identity.service.SellerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * セラー申請 API を表現します。
 *
 * <p>
 * 申請者は常にトークンの {@code sub}（user_id）から解決します。パス・ボディに user_id を取りません。
 * ロール判定は {@link SellerApplicationService} の責務です。
 */
@RestController
@RequestMapping("/api/v1/seller-applications")
@RequiredArgsConstructor
@Tag(name = "SellerApplication", description = "セラー申請")
public class SellerApplicationController {

    private final SellerApplicationService sellerApplicationService;

    /**
     * セラー申請を送信します。
     *
     * <p>
     * 単一の申請を指す {@code GET /seller-applications/{id}} が仕様に存在しないため
     * {@code Location} ヘッダーは付けません。
     */
    @PostMapping
    @Operation(summary = "セラー申請送信")
    public ResponseEntity<SellerApplicationResponse> apply(
            @AuthenticationPrincipal KivioUserDetails principal,
            @Valid @RequestBody CreateSellerApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerApplicationService.apply(principal.getUserId(), request));
    }

    /**
     * 認証中ユーザーの最新の申請状況を取得します（申請が無ければ 404）。
     */
    @GetMapping("/me")
    @Operation(summary = "自分のセラー申請状況")
    public ResponseEntity<SellerApplicationResponse> getMyLatest(
            @AuthenticationPrincipal KivioUserDetails principal) {
        return ResponseEntity.ok(sellerApplicationService.getMyLatest(principal.getUserId()));
    }
}
