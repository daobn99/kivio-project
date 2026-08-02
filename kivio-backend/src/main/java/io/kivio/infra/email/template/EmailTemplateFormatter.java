package io.kivio.infra.email.template;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * メールテンプレート（HTML）を読み込み、{@code {{変数名}}} を実値へ置換します。
 *
 * <p>テンプレートは classpath 上の HTML ファイル（例: {@code emails/ja/registration-otp.html}）。
 * 変数はテンプレートごとに定義された「本文変数」に対応します。
 */
@Component
public class EmailTemplateFormatter {

    /**
     * テンプレートを読み込み、変数を置換した HTML 文字列を返します。
     *
     * @param templatePath classpath 相対パス（例: {@code emails/ja/registration-otp.html}）
     * @param variables    {@code {{key}}} に対応する置換値
     * @return 変数置換済みの HTML
     */
    public String render(String templatePath, Map<String, String> variables) {
        String result = loadTemplate(templatePath);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String loadTemplate(String templatePath) {
        ClassPathResource resource = new ClassPathResource(templatePath);
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("メールテンプレートの読み込みに失敗しました: " + templatePath, e);
        }
    }
}
