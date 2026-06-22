package io.kivio.domain.identity.service;

import io.kivio.common.exception.ResourceNotFoundException;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.domain.UserRole;
import io.kivio.domain.identity.domain.UserStatus;
import io.kivio.domain.identity.dto.response.UserResponse;
import io.kivio.domain.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * {@link UserService} のプロフィール取得を単体検証します。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;

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
    void should_propagate_not_found_when_user_is_absent() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findByIdOrThrow(userId))
                .willThrow(new ResourceNotFoundException("User", userId));

        assertThatThrownBy(() -> userService.getById(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
