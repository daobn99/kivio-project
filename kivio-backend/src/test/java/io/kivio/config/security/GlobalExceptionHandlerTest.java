package io.kivio.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GlobalExceptionHandler} のうち、個別の {@code KivioException} を経由しない
 * インフラ由来の例外マッピングを検証します。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(handler, "problemBaseUrl", "https://kivio.example.com");
    }

    @Test
    void should_map_data_integrity_violation_to_409_duplicate_entry() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users/me/addresses");

        // 並行リクエストがデフォルト住所の部分 UNIQUE を同時に踏んだ場合など。
        // サーバー側の不具合ではないため 500 ではなく 409 を返す（実装計画 R-4）
        ProblemDetail problem = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"idx_addresses_user_default_unique\""),
                request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "DUPLICATE_ENTRY");
        assertThat(problem.getTitle()).isEqualTo("Duplicate Entry");
        assertThat(problem.getInstance()).hasToString("/api/v1/users/me/addresses");
        // DB のメッセージ（制約名・テーブル構造）をクライアントに漏らさない
        assertThat(problem.getDetail()).doesNotContain("idx_addresses", "constraint");
    }
}
