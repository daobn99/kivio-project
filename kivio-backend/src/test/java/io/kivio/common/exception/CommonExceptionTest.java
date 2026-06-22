package io.kivio.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共通例外（{@link KivioException} 派生）のコード・HTTP ステータス・メッセージを検証します。
 *
 * <p>サービス層のフローでは到達しづらい例外も含め、コントラクト（code/status）を固定する。
 */
class CommonExceptionTest {

    @Test
    void resource_not_found_with_detail_message() {
        ResourceNotFoundException ex = new ResourceNotFoundException("見つかりません");

        assertThat(ex.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("見つかりません");
    }

    @Test
    void resource_not_found_with_entity_and_id() {
        UUID id = UUID.randomUUID();
        ResourceNotFoundException ex = new ResourceNotFoundException("User", id);

        assertThat(ex.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("User").contains(id.toString());
    }

    @Test
    void token_expired_is_unauthorized() {
        TokenExpiredException ex = new TokenExpiredException();

        assertThat(ex.getCode()).isEqualTo("TOKEN_EXPIRED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void business_rule_is_unprocessable_content() {
        BusinessRuleException ex = new BusinessRuleException("SAMPLE_RULE", "業務ルール違反") {
        };

        assertThat(ex.getCode()).isEqualTo("SAMPLE_RULE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ex.getMessage()).isEqualTo("業務ルール違反");
    }
}
