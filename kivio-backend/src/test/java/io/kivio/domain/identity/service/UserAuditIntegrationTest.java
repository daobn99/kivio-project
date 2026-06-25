package io.kivio.domain.identity.service;

import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.dto.request.ChangePasswordRequest;
import io.kivio.domain.identity.exception.PasswordChangeFailedException;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code @Auditable} による監査ログ書き込みを実 DB で検証します（{@code UserService} 経路）。
 *
 * <p>
 * {@link io.kivio.domain.audit.annotation.AuditLogAspect} と
 * {@link io.kivio.domain.audit.annotation.AuditLogWriter} の連携（{@code REQUIRES_NEW} 独立
 * トランザクション・SUCCESS/FAILURE 記録・entity_id 抽出）を end-to-end で確認します。
 */
class UserAuditIntegrationTest extends IntegrationTestBase {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ============================================================
    // USER_PASSWORD_CHANGED
    // ============================================================

    @Test
    void changePassword_success_writes_success_audit_log() {
        User user = createUser("CorrectPassword1!");

        userService.changePassword(
                user.getId(),
                new ChangePasswordRequest("CorrectPassword1!", "NewPassword1!"));

        Map<String, Object> row = singleAuditRow("USER_PASSWORD_CHANGED", user.getId());
        assertThat(row.get("entity_type")).isEqualTo("USER");
        assertThat(row.get("outcome")).isEqualTo("SUCCESS");
        assertThat(row.get("error_message")).isNull();
        assertThat(row.get("correlation_id")).isNotNull();
    }

    @Test
    void changePassword_with_wrong_current_password_writes_failure_audit_log() {
        // REQUIRES_NEW により、業務トランザクションのロールバックに巻き込まれず FAILURE が残る。
        User user = createUser("CorrectPassword1!");

        assertThatThrownBy(() -> userService.changePassword(
                user.getId(),
                new ChangePasswordRequest("WrongPassword1!", "NewPassword1!")))
                .isInstanceOf(PasswordChangeFailedException.class);

        Map<String, Object> row = singleAuditRow("USER_PASSWORD_CHANGED", user.getId());
        assertThat(row.get("outcome")).isEqualTo("FAILURE");
        assertThat(row.get("error_message")).isNotNull();
    }

    @Test
    void changePassword_for_google_only_user_writes_failure_audit_log() {
        // passwordHash == null（Google ログイン専用）も PASSWORD_CHANGE_FAILED。
        User user = userRepository.save(User.builder()
                .email(uniqueEmail("google"))
                .googleId("google-" + UUID.randomUUID())
                .displayName("Google User")
                .build());

        assertThatThrownBy(() -> userService.changePassword(
                user.getId(),
                new ChangePasswordRequest("anything1!", "NewPassword1!")))
                .isInstanceOf(PasswordChangeFailedException.class);

        Map<String, Object> row = singleAuditRow("USER_PASSWORD_CHANGED", user.getId());
        assertThat(row.get("outcome")).isEqualTo("FAILURE");
    }

    // ============================================================
    // USER_WITHDRAWN
    // ============================================================

    @Test
    void withdraw_success_writes_success_audit_log() {
        User user = createUser("CorrectPassword1!");

        userService.withdraw(user.getId());

        Map<String, Object> row = singleAuditRow("USER_WITHDRAWN", user.getId());
        assertThat(row.get("entity_type")).isEqualTo("USER");
        assertThat(row.get("outcome")).isEqualTo("SUCCESS");
        // 論理削除されているため @SQLRestriction で通常クエリから除外される。
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void withdraw_for_unknown_user_writes_failure_audit_log() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.withdraw(unknownId))
                .isInstanceOf(RuntimeException.class);

        Map<String, Object> row = singleAuditRow("USER_WITHDRAWN", unknownId);
        assertThat(row.get("outcome")).isEqualTo("FAILURE");
        assertThat(row.get("error_message")).isNotNull();
    }

    // ============================================================
    // helpers
    // ============================================================

    private User createUser(String rawPassword) {
        return userRepository.save(User.builder()
                .email(uniqueEmail("audit"))
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName("Audit User")
                .build());
    }

    /** 指定 action / entity_id の監査ログがちょうど 1 件存在することを確認し、その行を返す。 */
    private Map<String, Object> singleAuditRow(String action, UUID entityId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE action = ? AND entity_id = ?",
                action, entityId);
        assertThat(rows)
                .as("action=%s entity_id=%s の監査ログが 1 件記録されていること", action, entityId)
                .hasSize(1);
        return rows.get(0);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
