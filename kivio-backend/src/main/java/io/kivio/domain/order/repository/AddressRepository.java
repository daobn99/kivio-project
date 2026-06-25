package io.kivio.domain.order.repository;

import io.kivio.domain.order.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * 配送先住所リポジトリを表現します。
 */
public interface AddressRepository extends JpaRepository<Address, UUID> {

    /**
     * 指定ユーザーの住所を「デフォルト住所を先頭 → {@code created_at} 昇順」で取得します
     */
    List<Address> findByUserIdOrderByIsDefaultDescCreatedAtAsc(UUID userId);

    /**
     * 指定ユーザーの全住所のデフォルトフラグを落とします（デフォルト付け替え用）。
     */
    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId")
    void clearDefaultForUser(@Param("userId") UUID userId);
}
