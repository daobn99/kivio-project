package io.kivio.domain.identity.controller;

import io.kivio.common.exception.ResourceNotFoundException;
import io.kivio.config.security.KivioUserDetails;
import io.kivio.domain.identity.domain.SellerApplicationStatus;
import io.kivio.domain.identity.dto.request.CreateSellerApplicationRequest;
import io.kivio.domain.identity.dto.response.SellerApplicationResponse;
import io.kivio.domain.identity.exception.SellerApplicationAlreadyApprovedException;
import io.kivio.domain.identity.exception.SellerApplicationPendingException;
import io.kivio.domain.identity.service.SellerApplicationService;
import io.kivio.support.ControllerTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * セラー申請 API の HTTP 仕様を {@code @WebMvcTest} スライスで検証します。
 *
 * <p>
 * {@link ControllerTestBase} が {@code SecurityConfig} を {@code @Import} しているため、
 * Filter Chain の認可（ロールガードを外した結果 SELLER / ADMIN が 403 ではなく Service に
 * 到達すること・実装計画 OQ-1）もこのスライスで直接検証できます。
 */
@WebMvcTest(SellerApplicationController.class)
class SellerApplicationControllerTest extends ControllerTestBase {

    @MockitoBean
    private SellerApplicationService sellerApplicationService;

    private static final String VALID_BODY = """
            {"reason":"ハンドメイド作品を販売したいため"}
            """;

    // ============================================================
    // POST /api/v1/seller-applications
    // ============================================================

    @Test
    void should_return_201_with_application_when_buyer_applies() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        given(sellerApplicationService.apply(eq(userId), any(CreateSellerApplicationRequest.class)))
                .willReturn(pending(applicationId, userId));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.applicantId").value(userId.toString()))
                .andExpect(jsonPath("$.reason").value("ハンドメイド作品を販売したいため"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists())
                // 単一の申請を指す GET が仕様に無いため Location は付けない
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void should_pass_only_token_subject_as_applicant_id() throws Exception {
        // ボディに他人の applicantId を混ぜても DTO に受け口が無く、Service にはトークンの sub だけが渡る
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        given(sellerApplicationService.apply(eq(userId), any(CreateSellerApplicationRequest.class)))
                .willReturn(pending(UUID.randomUUID(), userId));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"なりすまし","applicantId":"%s","status":"APPROVED"}
                                """.formatted(otherUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicantId").value(userId.toString()));

        verify(sellerApplicationService).apply(eq(userId), any(CreateSellerApplicationRequest.class));
        verify(sellerApplicationService, never())
                .apply(eq(otherUserId), any(CreateSellerApplicationRequest.class));
    }

    // 全角スペースのみ（U+3000）は含めない。@NotBlank は String.trim() 判定のため
    // U+3000 を空白とみなさず 201 になる（zod の .trim() とは挙動が異なる・要別途判断）
    @ParameterizedTest(name = "reason = [{0}]")
    @ValueSource(strings = {"", " ", "\\n", "\\t"})
    void should_return_422_when_reason_is_blank(String reason) throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/seller-applications")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"%s"}
                                """.formatted(reason)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("reason")));

        verify(sellerApplicationService, never())
                .apply(any(UUID.class), any(CreateSellerApplicationRequest.class));
    }

    @Test
    void should_return_422_when_reason_is_missing() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/seller-applications")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("reason")));
    }

    @Test
    void should_return_422_when_reason_exceeds_1000_characters() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/seller-applications")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"%s"}
                                """.formatted("あ".repeat(1001))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("reason")));
    }

    @Test
    void should_accept_reason_of_exactly_1000_characters() throws Exception {
        // 上限は「1000 文字以内」= 1000 文字ちょうどは通す（境界値）
        UUID userId = UUID.randomUUID();
        given(sellerApplicationService.apply(eq(userId), any(CreateSellerApplicationRequest.class)))
                .willReturn(pending(UUID.randomUUID(), userId));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"%s"}
                                """.formatted("あ".repeat(1000))))
                .andExpect(status().isCreated());
    }

    @Test
    void should_return_409_when_pending_application_exists() throws Exception {
        UUID userId = UUID.randomUUID();
        willThrow(new SellerApplicationPendingException())
                .given(sellerApplicationService)
                .apply(eq(userId), any(CreateSellerApplicationRequest.class));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SELLER_APPLICATION_PENDING"));
    }

    @Test
    void should_return_409_when_applicant_is_already_approved() throws Exception {
        UUID userId = UUID.randomUUID();
        willThrow(new SellerApplicationAlreadyApprovedException())
                .given(sellerApplicationService)
                .apply(eq(userId), any(CreateSellerApplicationRequest.class));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SELLER_APPLICATION_ALREADY_APPROVED"));
    }

    @ParameterizedTest(name = "role = {0}")
    @ValueSource(strings = {"ROLE_SELLER", "ROLE_ADMIN"})
    void should_return_409_not_403_when_non_buyer_applies(String role) throws Exception {
        // OQ-1: SecurityConfig のロールガードを外したため、SELLER / ADMIN は Filter Chain を
        // 通過して Service に到達し 409 になる。403 が返るならガードの削除漏れ（R-1）
        UUID userId = UUID.randomUUID();
        willThrow(new SellerApplicationAlreadyApprovedException())
                .given(sellerApplicationService)
                .apply(eq(userId), any(CreateSellerApplicationRequest.class));

        mockMvc.perform(post("/api/v1/seller-applications")
                        .with(user(new KivioUserDetails(userId, role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_APPLICATION_ALREADY_APPROVED"));

        // Filter Chain で弾かれず Service まで到達したことの裏付け
        verify(sellerApplicationService).apply(eq(userId), any(CreateSellerApplicationRequest.class));
    }

    @Test
    void should_return_401_when_applying_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/seller-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verify(sellerApplicationService, never())
                .apply(any(UUID.class), any(CreateSellerApplicationRequest.class));
    }

    // ============================================================
    // GET /api/v1/seller-applications/me
    // ============================================================

    @Test
    void should_omit_review_fields_when_latest_application_is_pending() throws Exception {
        // spring.jackson.default-property-inclusion: non_null により null はキーごと省略される
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        given(sellerApplicationService.getMyLatest(userId)).willReturn(pending(applicationId, userId));

        mockMvc.perform(get("/api/v1/seller-applications/me")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.applicantId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reviewComment").doesNotExist())
                .andExpect(jsonPath("$.reviewedAt").doesNotExist());
    }

    @Test
    void should_return_review_comment_when_latest_application_is_rejected() throws Exception {
        UUID userId = UUID.randomUUID();
        given(sellerApplicationService.getMyLatest(userId)).willReturn(SellerApplicationResponse.builder()
                .id(UUID.randomUUID())
                .applicantId(userId)
                .reason("申請理由")
                .status(SellerApplicationStatus.REJECTED)
                .reviewComment("事業内容の記載が不十分です")
                .reviewedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .createdAt(Instant.parse("2026-06-01T00:00:00Z"))
                .build());

        mockMvc.perform(get("/api/v1/seller-applications/me")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewComment").value("事業内容の記載が不十分です"))
                .andExpect(jsonPath("$.reviewedAt").value("2026-07-01T00:00:00Z"))
                // 審査者 ID は申請者に開示しない
                .andExpect(jsonPath("$.reviewerId").doesNotExist());
    }

    @Test
    void should_return_404_when_applicant_has_no_application() throws Exception {
        UUID userId = UUID.randomUUID();
        given(sellerApplicationService.getMyLatest(userId))
                .willThrow(new ResourceNotFoundException("セラー申請が見つかりません"));

        mockMvc.perform(get("/api/v1/seller-applications/me")
                        .with(user(new KivioUserDetails(userId, "ROLE_BUYER"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest(name = "role = {0}")
    @ValueSource(strings = {"ROLE_BUYER", "ROLE_SELLER", "ROLE_ADMIN"})
    void should_allow_all_roles_to_read_own_application_status(String role) throws Exception {
        // API_DESIGN.md §4 の「権限: 全ロール」
        UUID userId = UUID.randomUUID();
        given(sellerApplicationService.getMyLatest(userId))
                .willReturn(pending(UUID.randomUUID(), userId));

        mockMvc.perform(get("/api/v1/seller-applications/me")
                        .with(user(new KivioUserDetails(userId, role))))
                .andExpect(status().isOk());
    }

    @Test
    void should_return_401_when_reading_status_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/seller-applications/me"))
                .andExpect(status().isUnauthorized());

        verify(sellerApplicationService, never()).getMyLatest(any(UUID.class));
    }

    // ============================================================
    // helpers
    // ============================================================

    private SellerApplicationResponse pending(UUID applicationId, UUID applicantId) {
        return SellerApplicationResponse.builder()
                .id(applicationId)
                .applicantId(applicantId)
                .reason("ハンドメイド作品を販売したいため")
                .status(SellerApplicationStatus.PENDING)
                .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();
    }
}
