package io.kivio.domain.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link User} 集約ルートのドメインルール（部分更新・パスワード差し替え・状態判定）を
 * Spring を起動しない純粋 JUnit で検証します。
 */
class UserTest {

    private User sample() {
        return User.builder()
                .email("buyer@example.com")
                .displayName("Old Name")
                .avatarUrl("https://example.com/old.png")
                .passwordHash("$2a$12$oldhash")
                .build();
    }

    @Test
    void should_update_display_name_and_avatar_when_both_provided() {
        User user = sample();

        user.updateProfile("New Name", "https://example.com/new.png");

        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/new.png");
    }

    @Test
    void should_keep_unsent_fields_unchanged_when_null_is_passed() {
        User user = sample();

        // displayName のみ更新・avatarUrl は null（未送信）
        user.updateProfile("New Name", null);

        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/old.png");
    }

    @Test
    void should_replace_password_hash_when_changed() {
        User user = sample();

        user.changePassword("$2a$12$newhash");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newhash");
    }

    @Test
    void should_report_has_password_based_on_hash_presence() {
        User withPassword = sample();
        User googleOnly = User.builder()
                .email("google@example.com")
                .displayName("Google User")
                .googleId("google-sub-123")
                .build();

        assertThat(withPassword.hasPassword()).isTrue();
        assertThat(googleOnly.hasPassword()).isFalse();
    }

    @Test
    void should_be_active_when_status_is_active() {
        User active = User.builder()
                .email("a@example.com")
                .displayName("Active")
                .status(UserStatus.ACTIVE)
                .build();
        User inactive = User.builder()
                .email("b@example.com")
                .displayName("Inactive")
                .status(UserStatus.INACTIVE)
                .build();

        assertThat(active.isActive()).isTrue();
        assertThat(inactive.isActive()).isFalse();
    }
}
