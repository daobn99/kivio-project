package io.kivio.domain.order.controller;

import io.kivio.config.security.KivioUserDetails;
import io.kivio.domain.order.dto.request.CreateAddressRequest;
import io.kivio.domain.order.dto.request.UpdateAddressRequest;
import io.kivio.domain.order.dto.response.AddressResponse;
import io.kivio.domain.order.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 配送先住所 API を表現します。
 *
 * <p>
 * URL は {@code /api/v1/users/me/addresses}（identity 文脈）ですが、集約の所属は {@code order}
 * ドメインです。
 * 対象ユーザーは常にトークンの {@code sub}（user_id）から解決します。
 */
@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Address", description = "配送先住所管理")
public class AddressController {

    private final AddressService addressService;

    /**
     * 認証中ユーザーの配送先住所を一覧取得します（配列・非ページング）。
     */
    @GetMapping
    @Operation(summary = "配送先住所一覧")
    public ResponseEntity<List<AddressResponse>> list(
            @AuthenticationPrincipal KivioUserDetails principal) {
        return ResponseEntity.ok(addressService.list(principal.getUserId()));
    }

    /**
     * 配送先住所を追加します。
     */
    @PostMapping
    @Operation(summary = "配送先住所追加")
    public ResponseEntity<AddressResponse> create(
            @AuthenticationPrincipal KivioUserDetails principal,
            @Valid @RequestBody CreateAddressRequest request) {
        AddressResponse created = addressService.create(principal.getUserId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * 配送先住所を部分更新します（自分の住所のみ）。
     */
    @PatchMapping("/{id}")
    @Operation(summary = "配送先住所更新")
    public ResponseEntity<AddressResponse> update(
            @AuthenticationPrincipal KivioUserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAddressRequest request) {
        return ResponseEntity.ok(addressService.update(principal.getUserId(), id, request));
    }

    /**
     * 配送先住所を削除します（物理削除・自分の住所のみ）。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "配送先住所削除")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal KivioUserDetails principal,
            @PathVariable UUID id) {
        addressService.delete(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
