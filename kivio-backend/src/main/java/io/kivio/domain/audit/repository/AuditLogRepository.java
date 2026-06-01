package io.kivio.domain.audit.repository;

import io.kivio.domain.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, AuditLog.AuditLogId> {

    // audit_logs は追記専用 — DELETE / UPDATE 禁止（AUDIT.md §3.1）
    @Override
    default void deleteById(AuditLog.AuditLogId id) {
        throw new UnsupportedOperationException("audit_logs の削除は禁止されています");
    }

    @Override
    default void delete(AuditLog entity) {
        throw new UnsupportedOperationException("audit_logs の削除は禁止されています");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("audit_logs の削除は禁止されています");
    }

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt >= :since")
    long countSince(Instant since);
}
