package io.kivio.domain.identity.controller;

import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.exception.GoogleTokenInvalidException;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.domain.identity.service.AuthEmailService;
import io.kivio.infra.google.GoogleTokenVerifier;
import io.kivio.infra.google.GoogleUserInfo;
import io.kivio.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // 外部 HTTP 呼び出しを防ぐためモックに差し替える
    @MockitoBean private GoogleTokenVerifier googleTokenVerifier;
    // OTP メール送信をモックし、生成された OTP を捕捉してフローを進める
    @MockitoBean private AuthEmailService authEmailService;

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
        createUser(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/check-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    // ============================================================
    // 登録フロー（OTP 3 ステップ）
    // ============================================================

    @Test
    void should_complete_registration_through_otp_flow_and_auto_login() throws Exception {
        String email = uniqueEmail("reg");

        // Step1: 認証コード送信（202）
        mockMvc.perform(post("/api/v1/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresInSeconds").value(600));

        String otp = captureSentOtp(email);

        // Step2: 認証コード検証 → registrationToken（200）
        String verifyBody = mockMvc.perform(post("/api/v1/auth/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(1800))
                .andReturn().getResponse().getContentAsString();
        String registrationToken = readField(verifyBody, "registrationToken");

        // Step3: パスワード設定・登録完了 → 自動ログイン（201・トークン発行）
        mockMvc.perform(post("/api/v1/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registrationToken":"%s","password":"Password123!","passwordConfirm":"Password123!","displayName":"Alice"}
                                """.formatted(registrationToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));

        // ユーザーが作成されておりログインできること
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123!"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void should_return_409_when_request_otp_for_already_registered_email() throws Exception {
        String email = uniqueEmail("regdup");
        createUser(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void should_return_422_when_request_otp_email_is_invalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-valid"}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_return_400_when_otp_does_not_match() throws Exception {
        String email = uniqueEmail("otpbad");
        mockMvc.perform(post("/api/v1/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        String otp = captureSentOtp(email);
        String wrongOtp = otp.equals("000000") ? "111111" : "000000";

        mockMvc.perform(post("/api/v1/auth/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","otp":"%s"}
                                """.formatted(email, wrongOtp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    @Test
    void should_return_400_when_registration_token_is_invalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registrationToken":"%s","password":"Password123!","passwordConfirm":"Password123!","displayName":"Bob"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_SESSION_INVALID"));
    }

    // ============================================================
    // POST /api/v1/auth/login
    // ============================================================

    @Test
    void should_return_200_with_tokens_when_login_with_valid_credentials() throws Exception {
        String email = uniqueEmail("login");
        createUser(email, "Password123!");

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
        createUser(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"WrongPassword!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ============================================================
    // POST /api/v1/auth/google
    // ============================================================

    @Test
    void should_return_200_with_tokens_when_google_login_succeeds() throws Exception {
        String email = uniqueEmail("google");
        given(googleTokenVerifier.verify(anyString()))
                .willReturn(new GoogleUserInfo("google-sub-" + UUID.randomUUID(), email, "Google User"));

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
                .andExpect(jsonPath("$.code").value("GOOGLE_TOKEN_INVALID"));
    }

    // ============================================================
    // POST /api/v1/auth/refresh
    // ============================================================

    @Test
    void should_return_200_with_new_tokens_when_refresh_token_is_valid() throws Exception {
        String email = uniqueEmail("refresh");
        createUser(email, "Password123!");
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
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    // ============================================================
    // POST /api/v1/auth/logout
    // ============================================================

    @Test
    void should_return_204_and_invalidate_token_when_logout_is_authenticated() throws Exception {
        String email = uniqueEmail("logout");
        createUser(email, "Password123!");
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

    /** OTP 検証を経由せず、認証済みユーザーを直接作成する（ログイン系テスト用）。 */
    private User createUser(String email, String password) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .displayName("Test User")
                .build());
    }

    /** request-otp 呼び出しで emailSender に渡された OTP を捕捉する。 */
    private String captureSentOtp(String email) {
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(authEmailService).sendRegistrationOtp(eq(email), otpCaptor.capture());
        return otpCaptor.getValue();
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

        return Map.of(
                "accessToken", readField(body, "accessToken"),
                "refreshToken", readField(body, "refreshToken")
        );
    }

    private String readField(String json, String field) {
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) objectMapper.readValue(json, Map.class);
        return (String) parsed.get(field);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
