package io.kivio.domain.order.controller;

import io.kivio.domain.identity.domain.User;
import io.kivio.domain.identity.repository.UserRepository;
import io.kivio.domain.order.domain.Address;
import io.kivio.domain.order.repository.AddressRepository;
import io.kivio.support.IntegrationTestBase;
import io.kivio.support.TestJwtTokenFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 配送先住所 API の所有権チェック（403/404）・{@code isDefault} 付け替え・物理削除を
 * Testcontainers PostgreSQL 上で DB 検証します。
 */
class AddressControllerIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;

    private static final String CREATE_BODY = """
            {"recipientName":"山田太郎","postalCode":"100-0001","prefecture":"東京都",
             "city":"千代田区","addressLine":"千代田1-1","phoneNumber":"03-1234-5678","isDefault":true}
            """;

    // ============================================================
    // GET /api/v1/users/me/addresses
    // ============================================================

    @Test
    void should_return_only_own_addresses_as_array() throws Exception {
        User owner = createUser();
        User other = createUser();
        addressRepository.save(addressOf(owner.getId(), "山田太郎", true));
        addressRepository.save(addressOf(other.getId(), "他人花子", true));

        mockMvc.perform(get("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipientName").value("山田太郎"));
    }

    @Test
    void should_order_addresses_by_default_first_then_created_at_asc() throws Exception {
        User owner = createUser();
        // 作成順: first → second（非デフォルト）→ def（デフォルト・最後に作成）
        // sleep で created_at を確実に分離する（並び順の決定性を担保）
        addressRepository.save(addressOf(owner.getId(), "一番目", false));
        Thread.sleep(10);
        addressRepository.save(addressOf(owner.getId(), "二番目", false));
        Thread.sleep(10);
        addressRepository.save(addressOf(owner.getId(), "デフォルト", true));

        // デフォルトが先頭（作成は最後）→ 残りは created_at 昇順（一番目 → 二番目）
        mockMvc.perform(get("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].recipientName").value("デフォルト"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].recipientName").value("一番目"))
                .andExpect(jsonPath("$[2].recipientName").value("二番目"));
    }

    @Test
    void should_return_401_when_listing_without_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/addresses"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // POST /api/v1/users/me/addresses
    // ============================================================

    @Test
    void should_create_address_with_201_and_location_header() throws Exception {
        User owner = createUser();

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.recipientName").value("山田太郎"))
                .andExpect(jsonPath("$.isDefault").value(true));

        assertThat(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(owner.getId()))
                .hasSize(1);
    }

    @Test
    void should_demote_existing_default_when_creating_a_new_default_address() throws Exception {
        User owner = createUser();
        Address existing = addressRepository.save(addressOf(owner.getId(), "旧デフォルト", true));

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());

        List<Address> addresses =
                addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(owner.getId());
        assertThat(addresses).hasSize(2);
        // デフォルトは新規住所のみ（旧デフォルトは false に落ちている）
        assertThat(addresses).filteredOn(Address::isDefault).hasSize(1);
        assertThat(addressRepository.findById(existing.getId())).get()
                .extracting(Address::isDefault).isEqualTo(false);
    }

    @Test
    void should_return_422_when_postal_code_is_invalid() throws Exception {
        User owner = createUser();

        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"山田太郎","postalCode":"abc","prefecture":"東京都",
                                 "city":"千代田区","addressLine":"千代田1-1","phoneNumber":"03-1234-5678","isDefault":false}
                                """))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("postalCode")));
    }

    @Test
    void should_return_401_when_creating_without_authentication() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // PATCH /api/v1/users/me/addresses/{id}
    // ============================================================

    @Test
    void should_update_own_address() throws Exception {
        User owner = createUser();
        Address address = addressRepository.save(addressOf(owner.getId(), "山田太郎", false));

        mockMvc.perform(patch("/api/v1/users/me/addresses/{id}", address.getId())
                        .header("Authorization", bearer(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"山田次郎"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("山田次郎"));

        assertThat(addressRepository.findById(address.getId())).get()
                .extracting(Address::getRecipientName).isEqualTo("山田次郎");
    }

    @Test
    void should_promote_address_to_default_and_demote_the_previous_one() throws Exception {
        User owner = createUser();
        Address previous = addressRepository.save(addressOf(owner.getId(), "旧デフォルト", true));
        Address target = addressRepository.save(addressOf(owner.getId(), "新デフォルト", false));

        mockMvc.perform(patch("/api/v1/users/me/addresses/{id}", target.getId())
                        .header("Authorization", bearer(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isDefault":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        assertThat(addressRepository.findById(target.getId())).get()
                .extracting(Address::isDefault).isEqualTo(true);
        assertThat(addressRepository.findById(previous.getId())).get()
                .extracting(Address::isDefault).isEqualTo(false);
    }

    @Test
    void should_keep_default_when_reasserting_default_on_the_current_default_address()
            throws Exception {
        User owner = createUser();
        Address current = addressRepository.save(addressOf(owner.getId(), "デフォルト", true));

        // 既にデフォルトの住所に isDefault=true を再指定しても、デフォルトが消失しないこと
        mockMvc.perform(patch("/api/v1/users/me/addresses/{id}", current.getId())
                        .header("Authorization", bearer(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isDefault":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        // レスポンスと DB が一致していること（DB 上でデフォルトが落ちていない）
        assertThat(addressRepository.findById(current.getId())).get()
                .extracting(Address::isDefault).isEqualTo(true);
        assertThat(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(owner.getId()))
                .filteredOn(Address::isDefault).hasSize(1);
    }

    @Test
    void should_not_touch_other_users_default_address_when_setting_own_default() throws Exception {
        User owner = createUser();
        User other = createUser();
        Address ownerAddress = addressRepository.save(addressOf(owner.getId(), "自分", false));
        Address otherDefault = addressRepository.save(addressOf(other.getId(), "他人", true));

        mockMvc.perform(patch("/api/v1/users/me/addresses/{id}", ownerAddress.getId())
                        .header("Authorization", bearer(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isDefault":true}
                                """))
                .andExpect(status().isOk());

        // 付け替えは自ユーザーのスコープ内に閉じている
        assertThat(addressRepository.findById(otherDefault.getId())).get()
                .extracting(Address::isDefault).isEqualTo(true);
    }

    @Test
    void should_return_403_when_updating_another_users_address() throws Exception {
        User owner = createUser();
        User attacker = createUser();
        Address address = addressRepository.save(addressOf(owner.getId(), "山田太郎", false));

        mockMvc.perform(patch("/api/v1/users/me/addresses/{id}", address.getId())
                        .header("Authorization", bearer(attacker.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"乗っ取り"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 元の値が保持されていること
        assertThat(addressRepository.findById(address.getId())).get()
                .extracting(Address::getRecipientName).isEqualTo("山田太郎");
    }

    @Test
    void should_return_404_when_updating_absent_address() throws Exception {
        User owner = createUser();

        mockMvc.perform(patch("/api/v1/users/me/addresses/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"誰か"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_401_when_updating_without_authentication() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/addresses/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"誰か"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // DELETE /api/v1/users/me/addresses/{id}
    // ============================================================

    @Test
    void should_physically_delete_own_address() throws Exception {
        User owner = createUser();
        Address address = addressRepository.save(addressOf(owner.getId(), "山田太郎", false));

        mockMvc.perform(delete("/api/v1/users/me/addresses/{id}", address.getId())
                        .header("Authorization", bearer(owner.getId())))
                .andExpect(status().isNoContent());

        assertThat(addressRepository.findById(address.getId())).isEmpty();
    }

    @Test
    void should_return_403_when_deleting_another_users_address() throws Exception {
        User owner = createUser();
        User attacker = createUser();
        Address address = addressRepository.save(addressOf(owner.getId(), "山田太郎", false));

        mockMvc.perform(delete("/api/v1/users/me/addresses/{id}", address.getId())
                        .header("Authorization", bearer(attacker.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 削除されていないこと
        assertThat(addressRepository.findById(address.getId())).isPresent();
    }

    @Test
    void should_return_404_when_deleting_absent_address() throws Exception {
        User owner = createUser();

        mockMvc.perform(delete("/api/v1/users/me/addresses/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(owner.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_401_when_deleting_without_authentication() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/addresses/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // デフォルト住所の一意性（DB 制約・実装計画 R-4）
    // ============================================================

    @Test
    void should_reject_second_default_address_at_database_level() {
        User owner = createUser();
        addressRepository.saveAndFlush(addressOf(owner.getId(), "既定の住所", true));

        // アプリ側の付け替え手順を経由せずに 2 件目のデフォルトを差し込もうとしても、
        // 部分 UNIQUE インデックスが弾く（並行リクエストで両者が互いの未コミット行を
        // 見ないケースの最終防衛線・V4__create_order_tables.sql の idx_addresses_user_default_unique）
        Address second = addressOf(owner.getId(), "二つ目の既定", true);
        assertThatThrownBy(() -> addressRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_allow_multiple_non_default_addresses() {
        User owner = createUser();
        addressRepository.saveAndFlush(addressOf(owner.getId(), "その1", false));
        addressRepository.saveAndFlush(addressOf(owner.getId(), "その2", false));
        addressRepository.saveAndFlush(addressOf(owner.getId(), "その3", false));

        // 部分インデックスのため is_default = false は何件でも許容される
        assertThat(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(owner.getId()))
                .hasSize(3);
    }

    @Test
    void should_allow_one_default_address_per_user() {
        User owner = createUser();
        User other = createUser();

        addressRepository.saveAndFlush(addressOf(owner.getId(), "本人の既定", true));
        addressRepository.saveAndFlush(addressOf(other.getId(), "他人の既定", true));

        // 一意性はユーザー単位（user_id ごと）であり、ユーザーをまたいで衝突しない
        assertThat(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(owner.getId()))
                .singleElement().extracting(Address::isDefault).isEqualTo(true);
        assertThat(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(other.getId()))
                .singleElement().extracting(Address::isDefault).isEqualTo(true);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private User createUser() {
        return userRepository.save(User.builder()
                .email("addr-" + UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$12$dummdummdummdummdummdu")
                .displayName("Test User")
                .build());
    }

    private Address addressOf(UUID userId, String recipientName, boolean isDefault) {
        return Address.builder()
                .userId(userId)
                .recipientName(recipientName)
                .postalCode("100-0001")
                .prefecture("東京都")
                .city("千代田区")
                .addressLine("千代田1-1")
                .phoneNumber("03-1234-5678")
                .isDefault(isDefault)
                .build();
    }

    private String bearer(UUID userId) {
        return "Bearer " + TestJwtTokenFactory.buyerToken(userId);
    }
}
