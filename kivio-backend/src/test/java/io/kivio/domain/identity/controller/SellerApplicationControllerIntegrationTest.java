package io.kivio.domain.identity.controller;

import io.kivio.domain.identity.domain.SellerApplication;
import io.kivio.domain.identity.domain.SellerApplicationStatus;
import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.domain.UserRole;
import io.kivio.domain.identity.repository.SellerApplicationRepository;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.support.IntegrationTestBase;
import io.kivio.support.TestJwtTokenFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * セラー申請 API を Testcontainers PostgreSQL 上で end-to-end 検証します。
 *
 * <p>
 * スライステスト（{@link SellerApplicationControllerTest}）では確認できない
 * ① 実データでの 409 / 404 ② mass assignment の回帰 ③ 部分 UNIQUE インデックス
 * （{@code idx_seller_applications_pending_unique}）④ 監査ログ書き込みを対象とします。
 */
class SellerApplicationControllerIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SellerApplicationRepository sellerApplicationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String VALID_BODY = """
            {"reason":"ハンドメイド作品を販売したいため"}
            """;

    // ============================================================
    // POST /api/v1/seller-applications
    // ============================================================

    @Test
    void should_persist_pending_application_for_token_subject() throws Exception {
        User buyer = createUser(UserRole.ROLE_BUYER);

        mockMvc.perform(post("/api/v1/seller-applications")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.applicantId").value(buyer.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                // 未審査のフィールドは non_null によりレスポンスから省略される
                .andExpect(jsonPath("$.reviewComment").doesNotExist())
                .andExpect(jsonPath("$.reviewedAt").doesNotExist());

        Map<String, Object> row = singleApplicationRow(buyer.getId());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("applicant_id")).isEqualTo(buyer.getId());
        assertThat(row.get("reason")).isEqualTo("ハンドメイド作品を販売したいため");
        assertThat(row.get("reviewer_id")).isNull();
        assertThat(row.get("review_comment")).isNull();
        assertThat(row.get("reviewed_at")).isNull();
    }

    @Test
    void should_ignore_server_controlled_fields_in_the_request_body() throws Exception {
        // fail-on-unknown-properties: false のため未知フィールドは 400 にならず黙って無視される。
        // DTO が reason しか持たないことが唯一の防御線であることの回帰テスト
        User buyer = createUser(UserRole.ROLE_BUYER);
        User other = createUser(UserRole.ROLE_BUYER);

        mockMvc.perform(post("/api/v1/seller-applications")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"正当な申請理由","status":"APPROVED","applicantId":"%s",
                                 "reviewerId":"%s","reviewComment":"自分で承認しました",
                                 "reviewedAt":"2026-01-01T00:00:00Z"}
                                """.formatted(other.getId(), other.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.applicantId").value(buyer.getId().toString()));

        Map<String, Object> row = singleApplicationRow(buyer.getId());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("reviewer_id")).isNull();
        assertThat(row.get("review_comment")).isNull();
        assertThat(row.get("reviewed_at")).isNull();
        // 他人の ID に紐づく申請は作られていない
        assertThat(countApplications(other.getId())).isZero();
    }

    @Test
    void should_return_409_without_creating_a_second_row_when_pending_exists() throws Exception {
        User buyer = createUser(UserRole.ROLE_BUYER);
        sellerApplicationRepository.save(applicationOf(buyer.getId(), SellerApplicationStatus.PENDING));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_APPLICATION_PENDING"));

        assertThat(countApplications(buyer.getId())).isEqualTo(1);
    }

    @Test
    void should_return_409_when_approved_application_exists() throws Exception {
        // ロールが BUYER のまま APPROVED 申請を持つ不整合状態（承認処理は別スライス）でも塞ぐ
        User buyer = createUser(UserRole.ROLE_BUYER);
        sellerApplicationRepository.save(applicationOf(buyer.getId(), SellerApplicationStatus.APPROVED));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_APPLICATION_ALREADY_APPROVED"));

        assertThat(countApplications(buyer.getId())).isEqualTo(1);
    }

    @Test
    void should_create_a_new_row_when_only_rejected_applications_exist() throws Exception {
        // SELLER-05: 却下後の再申請は新レコード作成。過去の申請履歴は残る
        User buyer = createUser(UserRole.ROLE_BUYER);
        sellerApplicationRepository.save(applicationOf(buyer.getId(), SellerApplicationStatus.REJECTED));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(countApplications(buyer.getId())).isEqualTo(2);
        // 却下履歴が上書きされていないこと
        assertThat(statusesOf(buyer.getId()))
                .containsExactlyInAnyOrder("REJECTED", "PENDING");
    }

    @ParameterizedTest(name = "role = {0}")
    @ValueSource(strings = {"ROLE_SELLER", "ROLE_ADMIN"})
    void should_return_409_and_create_no_row_when_applicant_is_not_a_buyer(String role)
            throws Exception {
        // SecurityConfig のロールガードを外した以上（OQ-1）、Service のロール判定が
        // 唯一の認可ポイント。実 DB でそれが効いていることを裏付ける（403 ではなく 409）
        User user = createUser(UserRole.valueOf(role));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_APPLICATION_ALREADY_APPROVED"));

        assertThat(countApplications(user.getId())).isZero();
    }

    @Test
    void should_return_422_when_reason_is_blank() throws Exception {
        User buyer = createUser(UserRole.ROLE_BUYER);

        mockMvc.perform(post("/api/v1/seller-applications")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"   "}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(countApplications(buyer.getId())).isZero();
    }

    @Test
    void should_return_401_when_applying_without_authentication() throws Exception {
        mockMvc.perform(post("/api/v1/seller-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // GET /api/v1/seller-applications/me
    // ============================================================

    @Test
    void should_return_only_own_application() throws Exception {
        User owner = createUser(UserRole.ROLE_BUYER);
        User other = createUser(UserRole.ROLE_BUYER);
        sellerApplicationRepository.save(
                applicationOf(owner.getId(), SellerApplicationStatus.PENDING, "自分の申請理由"));
        sellerApplicationRepository.save(
                applicationOf(other.getId(), SellerApplicationStatus.PENDING, "他人の申請理由"));

        mockMvc.perform(get("/api/v1/seller-applications/me")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.reason").value("自分の申請理由"));
    }

    @Test
    void should_return_the_latest_application_when_reapplied_after_rejection() throws Exception {
        User buyer = createUser(UserRole.ROLE_BUYER);
        sellerApplicationRepository.saveAndFlush(
                applicationOf(buyer.getId(), SellerApplicationStatus.REJECTED, "1 回目の申請理由"));
        // created_at を確実に分離し、並び順を決定的にする
        Thread.sleep(10);
        sellerApplicationRepository.saveAndFlush(
                applicationOf(buyer.getId(), SellerApplicationStatus.PENDING, "2 回目の申請理由"));

        mockMvc.perform(get("/api/v1/seller-applications/me")
                        .header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reason").value("2 回目の申請理由"));
    }

    @Test
    void should_return_404_when_applicant_has_never_applied() throws Exception {
        // 404 は「未申請」という正常状態（フロントは null に変換して扱う）
        User buyer = createUser(UserRole.ROLE_BUYER);

        mockMvc.perform(get("/api/v1/seller-applications/me")
                        .header("Authorization", bearer(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_401_when_reading_status_without_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/seller-applications/me"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 監査ログ（SELLER_APPLICATION_SUBMITTED）
    // ============================================================

    @Test
    void should_write_audit_log_when_application_is_submitted() throws Exception {
        User buyer = createUser(UserRole.ROLE_BUYER);

        mockMvc.perform(post("/api/v1/seller-applications")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        Map<String, Object> row = singleAuditRow("SELLER_APPLICATION_SUBMITTED", buyer.getId());
        assertThat(row.get("entity_type")).isEqualTo("SELLER_APPLICATION");
        assertThat(row.get("outcome")).isEqualTo("SUCCESS");
        assertThat(row.get("correlation_id")).isNotNull();
        assertThat(row.get("actor_role")).isEqualTo("BUYER");
        // entityIdParam を指定していないため entity_id は null（実装計画 R-9）。
        // applicantId を指定すると申請 ID ではなく申請者 ID が入り entity_type と食い違う
        assertThat(row.get("entity_id")).isNull();
        // 申請理由（PII を含みうる自由記述）は監査ログに複製されない
        assertThat(row.get("old_value")).isNull();
        assertThat(row.get("new_value")).isNull();
    }

    @Test
    void should_write_failure_audit_log_when_application_is_rejected_by_role_check() throws Exception {
        User seller = createUser(UserRole.ROLE_SELLER);

        mockMvc.perform(post("/api/v1/seller-applications")
                        .header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());

        // REQUIRES_NEW の独立トランザクションのため、業務処理が例外で終わっても監査は残る
        Map<String, Object> row = singleAuditRow("SELLER_APPLICATION_SUBMITTED", seller.getId());
        assertThat(row.get("outcome")).isEqualTo("FAILURE");
        assertThat(row.get("error_message")).isNotNull();
    }

    // ============================================================
    // PENDING の一意性（部分 UNIQUE インデックス・OQ-3）
    // ============================================================

    @Test
    void should_reject_second_pending_application_at_database_level() {
        User buyer = createUser(UserRole.ROLE_BUYER);
        sellerApplicationRepository.saveAndFlush(
                applicationOf(buyer.getId(), SellerApplicationStatus.PENDING));

        // アプリ層の事前チェックを経由せず 2 件目の PENDING を差し込もうとしても、
        // 部分 UNIQUE インデックスが弾く（二重送信・並行リクエストの最終防衛線）
        SellerApplication second = applicationOf(buyer.getId(), SellerApplicationStatus.PENDING);
        assertThatThrownBy(() -> sellerApplicationRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_allow_multiple_rejected_applications_for_the_same_applicant() {
        User buyer = createUser(UserRole.ROLE_BUYER);
        sellerApplicationRepository.saveAndFlush(
                applicationOf(buyer.getId(), SellerApplicationStatus.REJECTED, "1 回目"));
        sellerApplicationRepository.saveAndFlush(
                applicationOf(buyer.getId(), SellerApplicationStatus.REJECTED, "2 回目"));
        sellerApplicationRepository.saveAndFlush(
                applicationOf(buyer.getId(), SellerApplicationStatus.REJECTED, "3 回目"));

        // 部分インデックス（WHERE status = 'PENDING'）のため REJECTED は何件でも許容される
        assertThat(countApplications(buyer.getId())).isEqualTo(3);
    }

    @Test
    void should_allow_one_pending_application_per_user() {
        User buyer = createUser(UserRole.ROLE_BUYER);
        User other = createUser(UserRole.ROLE_BUYER);

        sellerApplicationRepository.saveAndFlush(
                applicationOf(buyer.getId(), SellerApplicationStatus.PENDING));
        sellerApplicationRepository.saveAndFlush(
                applicationOf(other.getId(), SellerApplicationStatus.PENDING));

        // 一意性は applicant_id 単位であり、ユーザーをまたいで衝突しない
        assertThat(countApplications(buyer.getId())).isEqualTo(1);
        assertThat(countApplications(other.getId())).isEqualTo(1);
    }

    // ============================================================
    // helpers
    // ============================================================

    private User createUser(UserRole role) {
        return userRepository.save(User.builder()
                .email("seller-app-" + UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$12$dummdummdummdummdummdu")
                .displayName("Test User")
                .role(role)
                .build());
    }

    private SellerApplication applicationOf(UUID applicantId, SellerApplicationStatus status) {
        return applicationOf(applicantId, status, "既存の申請理由");
    }

    private SellerApplication applicationOf(
            UUID applicantId, SellerApplicationStatus status, String reason) {
        return SellerApplication.builder()
                .applicantId(applicantId)
                .reason(reason)
                .status(status)
                .build();
    }

    /** JWT の role クレームは DB 上のロールと揃える（Service は DB のロールで判定する）。 */
    private String bearer(User user) {
        String role = user.getRole().name().replace("ROLE_", "");
        return "Bearer " + TestJwtTokenFactory.generateToken(user.getId(), role);
    }

    private int countApplications(UUID applicantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM seller_applications WHERE applicant_id = ?",
                Integer.class, applicantId);
        return count == null ? 0 : count;
    }

    private List<String> statusesOf(UUID applicantId) {
        return jdbcTemplate.queryForList(
                "SELECT status FROM seller_applications WHERE applicant_id = ?",
                String.class, applicantId);
    }

    /** 申請がちょうど 1 件だけ存在することを確認し、その行を返す。 */
    private Map<String, Object> singleApplicationRow(UUID applicantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM seller_applications WHERE applicant_id = ?", applicantId);
        assertThat(rows)
                .as("applicant_id=%s のセラー申請が 1 件記録されていること", applicantId)
                .hasSize(1);
        return rows.get(0);
    }

    /** 指定 action / actor_id の監査ログがちょうど 1 件存在することを確認し、その行を返す。 */
    private Map<String, Object> singleAuditRow(String action, UUID actorId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM audit_logs WHERE action = ? AND actor_id = ?", action, actorId);
        assertThat(rows)
                .as("action=%s actor_id=%s の監査ログが 1 件記録されていること", action, actorId)
                .hasSize(1);
        return rows.get(0);
    }
}
