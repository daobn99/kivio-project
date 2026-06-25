package io.kivio.domain.identity.controller;

import io.kivio.config.security.KivioUserDetails;
import io.kivio.domain.identity.domain.UserRole;
import io.kivio.domain.identity.domain.UserStatus;
import io.kivio.domain.identity.dto.request.ChangePasswordRequest;
import io.kivio.domain.identity.dto.request.UpdateProfileRequest;
import io.kivio.domain.identity.dto.response.UserResponse;
import io.kivio.domain.identity.exception.PasswordChangeFailedException;
import io.kivio.domain.identity.service.UserService;
import io.kivio.support.ControllerTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest extends ControllerTestBase {

    @MockitoBean
    private UserService userService;

    // ============================================================
    // GET /api/v1/users/me
    // ============================================================

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

    // ============================================================
    // PATCH /api/v1/users/me
    // ============================================================

    @Test
    void should_return_updated_profile_when_patching_me() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse response = UserResponse.builder()
                .id(userId)
                .email("buyer@example.com")
                .displayName("New Name")
                .role(UserRole.ROLE_BUYER)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.parse("2026-06-01T00:00:00Z"))
                .build();
        given(userService.updateProfile(eq(userId), any(UpdateProfileRequest.class)))
                .willReturn(response);

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"));
    }

    @Test
    void should_return_422_when_patching_me_with_blank_display_name() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":""}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("displayName")));
    }

    @Test
    void should_return_401_when_patching_me_unauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"New Name"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // PATCH /api/v1/users/me/password
    // ============================================================

    @Test
    void should_return_204_when_password_change_succeeds() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"CurrentPass1!","newPassword":"NewPassword1!"}
                                """))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));
    }

    @Test
    void should_return_400_when_current_password_is_incorrect() throws Exception {
        UUID userId = UUID.randomUUID();
        willThrow(new PasswordChangeFailedException())
                .given(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"WrongPass1!","newPassword":"NewPassword1!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_FAILED"));
    }

    @Test
    void should_return_422_when_new_password_is_too_short() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"CurrentPass1!","newPassword":"short"}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("newPassword")))
                // 機微フィールドの rejectedValue はマスキングされ出力されない
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
    }

    // ============================================================
    // DELETE /api/v1/users/me
    // ============================================================

    @Test
    void should_return_204_when_withdrawing() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/me")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER"))))
                .andExpect(status().isNoContent());

        verify(userService).withdraw(userId);
    }

    @Test
    void should_return_401_when_withdrawing_unauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
