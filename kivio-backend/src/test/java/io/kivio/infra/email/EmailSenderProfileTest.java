package io.kivio.infra.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
}
