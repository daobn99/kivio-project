package io.kivio.domain.identity.controller;

import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.exception.GoogleTokenInvalidException;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.infra.google.GoogleTokenVerifier;
import io.kivio.infra.google.GoogleUserInfo;
import io.kivio.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // 外部 HTTP 呼び出しを防ぐためモックに差し替える
    @MockitoBean private GoogleTokenVerifier googleTokenVerifier;

    // ============================================================
    // POST /api/v1/auth/check-email
    // ============================================================

    @Test
    void should_return_available_true_when_email_is_not_taken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/check-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"brand-new@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void should_return_available_false_when_email_is_already_registered() throws Exception {
        String email = uniqueEmail("ce");
        createVerifiedUser(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/check-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    // ============================================================
    // POST /api/v1/auth/register
    // ============================================================

    @Test
    void should_return_201_with_user_info_when_register_succeeds() throws Exception {
        String email = uniqueEmail("reg");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123!","passwordConfirm":"Password123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/api/v1/users/")))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("ROLE_BUYER"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void should_return_409_when_email_is_already_registered() throws Exception {
        String email = uniqueEmail("regdup");
        createVerifiedUser(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123!","passwordConfirm":"Password123!"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void should_return_422_when_register_request_is_invalid() throws Exception {
        // password が短すぎる + email 形式不正
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-valid","password":"short","passwordConfirm":"short"}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ============================================================
    // POST /api/v1/auth/login
    // ============================================================

    @Test
    void should_return_200_with_tokens_when_login_with_valid_credentials() throws Exception {
        String email = uniqueEmail("login");
        createVerifiedUser(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void should_return_401_when_login_credentials_are_invalid() throws Exception {
        String email = uniqueEmail("loginfail");
        createVerifiedUser(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"WrongPassword!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    // ============================================================
    // POST /api/v1/auth/google
    // ============================================================

    @Test
    void should_return_200_with_tokens_when_google_login_succeeds() throws Exception {
        String email = uniqueEmail("google");
        given(googleTokenVerifier.verify(anyString()))
                .willReturn(new GoogleUserInfo("google-sub-" + UUID.randomUUID(), email));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"valid-google-id-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void should_return_401_when_google_id_token_is_invalid() throws Exception {
        given(googleTokenVerifier.verify(anyString()))
                .willThrow(new GoogleTokenInvalidException());

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken":"invalid-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("GOOGLE_TOKEN_INVALID"));
    }

    // ============================================================
    // POST /api/v1/auth/refresh
    // ============================================================

    @Test
    void should_return_200_with_new_tokens_when_refresh_token_is_valid() throws Exception {
        String email = uniqueEmail("refresh");
        createVerifiedUser(email, "Password123!");
        Map<String, String> tokens = loginAndGetTokens(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(tokens.get("refreshToken"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void should_return_401_when_refresh_token_is_invalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"completely-invalid-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_INVALID"));
    }

    // ============================================================
    // POST /api/v1/auth/logout
    // ============================================================

    @Test
    void should_return_204_and_invalidate_token_when_logout_is_authenticated() throws Exception {
        String email = uniqueEmail("logout");
        createVerifiedUser(email, "Password123!");
        Map<String, String> tokens = loginAndGetTokens(email, "Password123!");
        String accessToken = tokens.get("accessToken");
        String refreshToken = tokens.get("refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        // ログアウト後は同じ Refresh Token でリフレッシュできないこと
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_401_when_logout_without_authentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"some-token"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // Helpers
    // ============================================================

    private User createVerifiedUser(String email, String password) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        user.verifyEmail();
        return userRepository.save(user);
    }

    private Map<String, String> loginAndGetTokens(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) objectMapper.readValue(body, Map.class);
        return Map.of(
                "accessToken", (String) parsed.get("accessToken"),
                "refreshToken", (String) parsed.get("refreshToken")
        );
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
