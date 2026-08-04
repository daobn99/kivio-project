package io.kivio.infra.email;

import io.kivio.config.EmailProperties;
import io.kivio.config.ResendProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * フォールバック {@link LogEmailSender} のプロファイル有効範囲を検証します。
 *
 * <p>dev（SMTP→Mailpit）・prod（Resend）以外（特に統合テストが使う {@code test} プロファイル）では
 * {@link LogEmailSender} が有効になり {@link EmailSender} を一意に提供すること、dev/prod では除外されることを保証する。
 * {@code test} プロファイルに EmailSender 実体が存在しないと、
 * フルコンテキストの起動（{@code KivioBackendApplicationTests}）が失敗する退行を防ぐ。
 *
 * <p>プロファイルは {@code withPropertyValues} で明示指定する（実行環境の {@code SPRING_PROFILES_ACTIVE} に依存しない）。
 * Testcontainers を使わない軽量テストのため Docker 不要で常時実行される。
 */
class EmailSenderProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LogEmailSender.class);

    @Test
    void fallback_is_active_under_test_profile() {
        runner.withPropertyValues("spring.profiles.active=test").run(context -> {
            assertThat(context).hasSingleBean(EmailSender.class);
            assertThat(context).getBean(EmailSender.class).isInstanceOf(LogEmailSender.class);
        });
    }

    @Test
    void fallback_is_excluded_under_dev_profile() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).doesNotHaveBean(EmailSender.class));
    }

    @Test
    void fallback_is_excluded_under_prod_profile() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(EmailSender.class));
    }

    /**
     * prod で実トランスポートが一意に提供されることを検証します。
     *
     * <p>{@link LogEmailSender} が prod を除外しているため、prod 用実装が欠けると
     * {@code EmailSender} を必須依存とする {@code AuthEmailService} の生成が失敗し、
     * 本番の起動そのものが落ちる。その退行を防ぐ。
     */
    @Test
    void resend_transport_is_active_under_prod_profile() {
        new ApplicationContextRunner()
                .withUserConfiguration(LogEmailSender.class, ResendEmailSender.class)
                .withBean(EmailProperties.class,
                        () -> new EmailProperties("noreply@kivio.example.com", "Kivio"))
                .withBean(ResendProperties.class,
                        () -> new ResendProperties("re_test_key", "http://localhost:1"))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailSender.class);
                    assertThat(context).getBean(EmailSender.class)
                            .isInstanceOf(ResendEmailSender.class);
                });
    }

    @Test
    void resend_transport_is_excluded_under_dev_profile() {
        new ApplicationContextRunner()
                .withUserConfiguration(ResendEmailSender.class)
                .withBean(EmailProperties.class,
                        () -> new EmailProperties("noreply@kivio.example.com", "Kivio"))
                .withBean(ResendProperties.class,
                        () -> new ResendProperties("re_test_key", "http://localhost:1"))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).doesNotHaveBean(EmailSender.class));
    }
}
