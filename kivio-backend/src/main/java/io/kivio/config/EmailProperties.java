package io.kivio.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 送信メールの差出人情報を表現します。
 *
 * <p>EMAIL_DESIGN.md §1「送信設定」に対応する。dev・prod の各 {@code EmailSender} 実装が共有する。
 */
@Validated
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        /** 差出人メールアドレス（例: noreply@kivio.example.com） */
        @NotBlank @Email String fromAddress,
        /** 差出人表示名（例: Kivio） */
        @NotBlank String fromName
) {
}
