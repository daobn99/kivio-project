package io.kivio.domain.identity.controller;

import io.kivio.config.security.KivioUserDetails;
import io.kivio.domain.identity.domain.UserRole;
import io.kivio.domain.identity.domain.UserStatus;
import io.kivio.domain.identity.dto.response.UserResponse;
import io.kivio.domain.identity.service.UserService;
import io.kivio.support.ControllerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest extends ControllerTestBase {

    @MockitoBean
    private UserService userService;

    @Test
    void should_return_current_user_profile_when_authenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse response = UserResponse.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("Buyer Taro")
                .role(UserRole.ROLE_BUYER)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-06-01T00:00:00Z"))
                .build();
        given(userService.getById(userId)).willReturn(response);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("buyer@example.com"))
                .andExpect(jsonPath("$.displayName").value("Buyer Taro"))
                .andExpect(jsonPath("$.role").value("ROLE_BUYER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void should_return_401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
