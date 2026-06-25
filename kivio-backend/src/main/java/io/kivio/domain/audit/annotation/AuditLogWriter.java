package io.kivio.domain.audit.annotation;

import io.kivio.domain.audit.domain.AuditLog;
import io.kivio.domain.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 監査ログを独立トランザクションで書き込むコンポーネントを表現します。
 *
 * <p>
 * {@link Propagation#REQUIRES_NEW} により呼び出し元のビジネストランザクションから切り離して
 * コミットします。これにより、業務処理が例外でロールバックされる場合（{@code outcome=FAILURE}）
 * でも監査ログが消えずに永続化されます。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    /**
     * 監査ログを独立トランザクションで保存します。
     *
     * <p>
     * 監査ログの保存失敗が業務処理に影響しないよう、例外は捕捉してログ出力にとどめます。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for action={}", auditLog.getAction(), e);
        }
    }
}
