package io.kivio.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 全エンティティ共通の監査フィールドを表現します。
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    /** 作成日時 */
    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    /** 更新日時 */
    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
