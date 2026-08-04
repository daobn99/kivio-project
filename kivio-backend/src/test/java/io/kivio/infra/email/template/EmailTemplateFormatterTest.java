package io.kivio.infra.email.template;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EmailTemplateFormatter} のテンプレート読み込み・変数置換を単体検証します。
 */
class EmailTemplateFormatterTest {

    private final EmailTemplateFormatter formatter = new EmailTemplateFormatter();

    @Test
    void should_replace_placeholders_in_real_otp_template() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("otpCode", "123456");
        variables.put("expiresIn", "10分");

        String html = formatter.render("emails/ja/registration-otp.html", variables);

        assertThat(html).contains("123456");
        assertThat(html).contains("10分");
        // すべてのプレースホルダが置換されていること
        assertThat(html).doesNotContain("{{otpCode}}");
        assertThat(html).doesNotContain("{{expiresIn}}");
    }

    @Test
    void should_leave_template_unchanged_when_no_variables_given() {
        String html = formatter.render("emails/ja/registration-otp.html", Map.of());

        // 置換対象がないため、未解決のプレースホルダはそのまま残る
        assertThat(html).contains("{{otpCode}}");
    }

    @Test
    void should_throw_when_template_is_missing() {
        assertThatThrownBy(() ->
                formatter.render("emails/ja/does-not-exist.html", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("emails/ja/does-not-exist.html");
    }
}
