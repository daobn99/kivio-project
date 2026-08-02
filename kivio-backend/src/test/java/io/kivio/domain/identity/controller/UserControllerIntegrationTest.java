package io.kivio.domain.identity.controller;

import io.kivio.domain.identity.domain.RefreshToken;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.repository.RefreshTokenRepository;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.support.IntegrationTestBase;
import io.kivio.support.TestJwtTokenFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ユーザー API のうち DB 挙動に依存するセキュリティ要件を Testcontainers PostgreSQL 上で検証します
 * （実装計画 §8 Security Checklist の「退会・論理削除」「パスワード」項目の裏付け）。
 *
 * <p>
 * スライステスト（{@code UserControllerTest}）ではサービスをモックするため、
 * {@code @SQLRestriction} による除外や BCrypt の実ハッシュは検証できません。
 */
class UserControllerIntegrationTest extends IntegrationTestBase {

    private static final String RAW_PASSWORD = "CurrentPass1!";
    private static final String NEW_PASSWORD = "NewPass1234!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ============================================================
    // DELETE /api/v1/users/me（退会・論理削除）
    // ============================================================

    @Test
    void should_soft_delete_user_and_keep_row_in_database() throws Exception {
        User user = createUser();

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isNoContent());

        // 物理削除ではなく deleted_at が設定されている（行は残る）
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class, user.getId());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void should_exclude_withdrawn_user_from_normal_queries() throws Exception {
        User user = createUser();

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isNoContent());

        // @SQLRestriction("deleted_at IS NULL") により通常クエリから除外される
        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(userRepository.findByEmail(user.getEmail())).isEmpty();
        assertThat(userRepository.existsByEmail(user.getEmail())).isFalse();
    }

    @Test
    void should_reject_access_with_still_valid_token_after_withdrawal() throws Exception {
        User user = createUser();

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isNoContent());

        // アクセストークンは有効期限内でも、対象ユーザーが解決できないため 404（OQ-3 の確定挙動）
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_delete_refresh_tokens_when_withdrawing() throws Exception {
        User user = createUser();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash("hash-" + UUID.randomUUID())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", bearer(user.getId())))
                .andExpect(status().isNoContent());

        // 有効期限内でも退会時に破棄する（RefreshTokenPurgeJob は expires_at 基準のため最長 7 日残る）
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?",
                Integer.class, user.getId());
        assertThat(remaining).isZero();
    }

    // ============================================================
    // PATCH /api/v1/users/me/password（パスワード変更）
    // ============================================================

    @Test
    void should_rehash_password_with_bcrypt_cost_12() throws Exception {
        User user = createUser();
        String oldHash = user.getPasswordHash();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(RAW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        String newHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, user.getId());
        assertThat(newHash).isNotEqualTo(oldHash);
        // BCrypt コストファクター 12（$2a$12$...）で再ハッシュされている
        assertThat(newHash).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, newHash)).isTrue();
        assertThat(passwordEncoder.matches(RAW_PASSWORD, newHash)).isFalse();
    }

    @Test
    void should_not_change_password_when_current_password_is_incorrect() throws Exception {
        User user = createUser();
        String oldHash = user.getPasswordHash();

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"WrongPass1!","newPassword":"%s"}
                                """.formatted(NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_FAILED"))
                // 機微情報（入力パスワード）をレスポンスに含めない
                .andExpect(jsonPath("$.detail").value("現在のパスワードが正しくありません"));

        String currentHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, user.getId());
        assertThat(currentHash).isEqualTo(oldHash);
    }

    @Test
    void should_reject_password_change_for_google_only_user() throws Exception {
        User user = userRepository.save(User.builder()
                .email("google-" + UUID.randomUUID() + "@example.com")
                .googleId("google-sub-" + UUID.randomUUID())
                .displayName("Google User")
                .build());

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", bearer(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"CurrentPass1!","newPassword":"%s"}
                                """.formatted(NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_FAILED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, user.getId()))
                .isNull();
    }

    // ============================================================
    // PATCH /api/v1/users/me（プロフィール更新）
    // ============================================================

    @Test
    void should_resolve_target_user_from_token_and_ignore_body_user_id() throws Exception {
        User owner = createUser();
        User other = createUser();

        // ボディに他人の userId / id を混ぜても、更新対象はトークンの sub のみ
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", bearer(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","userId":"%s","displayName":"更新後の名前"}
                                """.formatted(other.getId(), other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.displayName").value("更新後の名前"));

        assertThat(userRepository.findById(other.getId())).get()
                .extracting(User::getDisplayName).isEqualTo("Test User");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private User createUser() {
        return userRepository.save(User.builder()
                .email("user-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .displayName("Test User")
                .build());
    }

    private String bearer(UUID userId) {
        return "Bearer " + TestJwtTokenFactory.buyerToken(userId);
    }
}
