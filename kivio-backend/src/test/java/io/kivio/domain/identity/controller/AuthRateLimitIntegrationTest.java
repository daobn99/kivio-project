package io.kivio.domain.identity.controller;

import io.kivio.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 認証系エンドポイントのレート制限（10 req/min/IP）を検証します。
 *
 * <p>{@code test} プロファイルの既定容量（10000）ではレート制限を踏めないため、
 * {@link TestPropertySource} で本番既定値（10）まで下げた専用コンテキストで検証します。
 * 容量を変えることで Spring のコンテキストキャッシュ上も他テストと隔離され、
 * バケット（IP 単位）は空の状態から開始します。
 */
@TestPropertySource(properties = "app.rate-limit.auth.capacity=10")
class AuthRateLimitIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;

    @Test
    void should_return_429_with_retry_after_when_auth_rate_limit_is_exceeded() throws Exception {
        // 容量 10 のバケットを使い切る（10 回までは通過する）
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/check-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"rate-limit@example.com"}
                                    """))
                    .andExpect(status().isOk());
        }

        // 11 回目で上限超過 → 429 + Retry-After
        mockMvc.perform(post("/api/v1/auth/check-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"rate-limit@example.com"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
