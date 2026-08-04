package io.kivio.domain.health.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * ヘルスチェック API を表現します。
 */
@Tag(name = "Health", description = "ヘルスチェック")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final Clock clock;

    @Operation(summary = "ヘルスチェック", description = "サービスの稼働状態を確認します（認証不要）")
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", OffsetDateTime.now(clock)));
    }
}
