package io.kivio.domain.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@IdClass(AuditLog.AuditLogId.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditLog {

    /** ログID */
    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_logs_seq")
    @SequenceGenerator(name = "audit_logs_seq", sequenceName = "audit_logs_id_seq", allocationSize = 1)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** 作成日時 */
    @Id
    @EqualsAndHashCode.Include
    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** 相関ID */
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    /** 操作者ユーザーID */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    /** 操作者ロール */
    @Column(name = "actor_role", length = 20, updatable = false)
    private String actorRole;

    /** 操作者メール */
    @Column(name = "actor_email", length = 255, updatable = false)
    private String actorEmail;

    /** アクション */
    @Column(name = "action", length = 100, nullable = false, updatable = false)
    private String action;

    /** エンティティ種別 */
    @Column(name = "entity_type", length = 50, updatable = false)
    private String entityType;

    /** エンティティID */
    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    /** 結果 */
    @Builder.Default
    @Column(name = "outcome", length = 10, nullable = false, updatable = false)
    private String outcome = "SUCCESS";

    /** IPアドレス */
    @Column(name = "ip_address", updatable = false)
    private String ipAddress;

    /** 変更前の値 */
    @Column(name = "old_value", columnDefinition = "jsonb", updatable = false)
    private String oldValue;

    /** 変更後の値 */
    @Column(name = "new_value", columnDefinition = "jsonb", updatable = false)
    private String newValue;

    /** エラーメッセージ */
    @Column(name = "error_message", updatable = false)
    private String errorMessage;

    public record AuditLogId(Long id, Instant createdAt) implements Serializable {
    }
}
