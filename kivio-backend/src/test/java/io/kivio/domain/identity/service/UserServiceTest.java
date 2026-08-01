package io.kivio.domain.identity.service;

import io.kivio.common.exception.ResourceNotFoundException;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.domain.UserRole;
import io.kivio.domain.identity.domain.UserStatus;
import io.kivio.domain.identity.dto.request.ChangePasswordRequest;
import io.kivio.domain.identity.dto.request.UpdateProfileRequest;
import io.kivio.domain.identity.dto.response.UserResponse;
import io.kivio.domain.identity.exception.PasswordChangeFailedException;
import io.kivio.domain.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link UserService} のプロフィール取得・更新・パスワード変更・退会を単体検証します。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    // ============================================================
    // getById
    // ============================================================

    @Test
    void should_return_profile_when_user_exists() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("Buyer Taro")
                .role(UserRole.ROLE_BUYER)
                .status(UserStatus.ACTIVE)
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);

        UserResponse response = userService.getById(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("buyer@example.com");
        assertThat(response.displayName()).isEqualTo("Buyer Taro");
        assertThat(response.role()).isEqualTo(UserRole.ROLE_BUYER);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void should_expose_has_password_true_when_password_is_set() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("Buyer Taro")
                .passwordHash("$2a$12$hash")
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);

        assertThat(userService.getById(userId).hasPassword()).isTrue();
    }

    @Test
    void should_expose_has_password_false_for_google_only_user() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("google@example.com")
                .displayName("Google User")
                .googleId("google-sub-123")
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);

        assertThat(userService.getById(userId).hasPassword()).isFalse();
    }

    @Test
    void should_propagate_not_found_when_user_is_absent() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findByIdOrThrow(userId))
                .willThrow(new ResourceNotFoundException("User", userId));

        assertThatThrownBy(() -> userService.getById(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // updateProfile
    // ============================================================

    @Test
    void should_update_display_name_and_avatar_when_both_provided() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("Old Name")
                .avatarUrl("https://example.com/old.png")
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);

        UserResponse response = userService.updateProfile(userId,
                new UpdateProfileRequest("New Name", "https://example.com/new.png"));

        assertThat(response.displayName()).isEqualTo("New Name");
        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/new.png");
    }

    @Test
    void should_update_only_provided_fields_and_keep_unsent_fields_unchanged() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("Old Name")
                .avatarUrl("https://example.com/old.png")
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);

        // avatarUrl のみ未送信（null）→ 表示名のみ更新・アバターは不変
        userService.updateProfile(userId, new UpdateProfileRequest("New Name", null));

        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/old.png");
    }

    @Test
    void should_clear_avatar_url_when_empty_string_is_sent() {
        // 空文字 = クリアの意思表示（未送信の null とは区別する）
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("Buyer Taro")
                .avatarUrl("https://example.com/old.png")
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);

        UserResponse response = userService.updateProfile(userId, new UpdateProfileRequest(null, ""));

        assertThat(user.getAvatarUrl()).isNull();
        assertThat(response.avatarUrl()).isNull();
        assertThat(user.getDisplayName()).isEqualTo("Buyer Taro");
    }

    // ============================================================
    // changePassword
    // ============================================================

    @Test
    void should_rehash_password_when_current_password_matches() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("Buyer Taro")
                .passwordHash("$2a$12$oldhash")
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);
        given(passwordEncoder.matches("CurrentPass1!", "$2a$12$oldhash")).willReturn(true);
        given(passwordEncoder.encode("NewPassword1!")).willReturn("$2a$12$newhash");

        userService.changePassword(userId, new ChangePasswordRequest("CurrentPass1!", "NewPassword1!"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newhash");
    }

    @Test
    void should_fail_password_change_when_current_password_does_not_match() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("Buyer Taro")
                .passwordHash("$2a$12$oldhash")
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);
        given(passwordEncoder.matches("WrongPass1!", "$2a$12$oldhash")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword(userId,
                new ChangePasswordRequest("WrongPass1!", "NewPassword1!")))
                .isInstanceOf(PasswordChangeFailedException.class);

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$oldhash");
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void should_fail_password_change_when_user_has_no_password() {
        // Google ログイン専用ユーザー（passwordHash == null）
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("google@example.com")
                .displayName("Google User")
                .googleId("google-sub-123")
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);

        assertThatThrownBy(() -> userService.changePassword(userId,
                new ChangePasswordRequest("AnyPass1!", "NewPassword1!")))
                .isInstanceOf(PasswordChangeFailedException.class);

        assertThat(user.getPasswordHash()).isNull();
    }

    // ============================================================
    // withdraw
    // ============================================================

    @Test
    void should_set_deleted_at_when_withdrawing() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("Buyer Taro")
                .build();
        given(userRepository.findByIdOrThrow(userId)).willReturn(user);

        userService.withdraw(userId);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
    }
}
