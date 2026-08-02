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
     * 指定ユーザーの全住所のデフォルトフラグを落とします（新規住所をデフォルトにする際に使用）。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId")
    void clearDefaultForUser(@Param("userId") UUID userId);

    /**
     * 指定住所を除く、指定ユーザーの住所のデフォルトフラグを落とします（既存住所への付け替え用）。
     *
     * <p>
     * 対象住所自身を除外するのは、既にデフォルトの住所に対して {@code isDefault = true} を再指定した際に
     * 一括 UPDATE で {@code false} に落ちたまま（エンティティに変更がなく dirty checking で復元されない）
     * デフォルト住所が消失するのを防ぐためです。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId AND a.id <> :excludedId")
    void clearDefaultForUserExcept(@Param("userId") UUID userId, @Param("excludedId") UUID excludedId);
}
