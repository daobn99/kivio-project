package io.kivio.domain.audit.annotation;

import io.kivio.config.security.KivioUserDetails;
import io.kivio.domain.audit.domain.AuditLog;
import io.kivio.domain.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * @Auditable アノテーションに基づく監査ログ記録 AOP を表現します。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        String correlationIdStr = MDC.get("correlationId");
        UUID correlationId = correlationIdStr != null
                ? UUID.fromString(correlationIdStr)
                : UUID.randomUUID();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID actorId = extractUserId(auth);
        String actorRole = extractRole(auth);
        String actorEmail = extractEmail(auth);
        String ipAddress = getClientIp();

        String outcome = "SUCCESS";
        String errorMessage = null;

        UUID entityId = extractEntityId(pjp, auditable.entityIdParam());

        if (auditable.captureOldValue() || auditable.captureNewValue()) {
            log.warn("captureOldValue/captureNewValue は未実装です action={}", auditable.action());
        }

        try {
            return pjp.proceed();
        } catch (Exception e) {
            outcome = "FAILURE";
            errorMessage = e.getMessage();
            throw e;
        } finally {
            try {
                auditLogRepository.save(AuditLog.builder()
                        .correlationId(correlationId)
                        .actorId(actorId)
                        .actorRole(actorRole)
                        .actorEmail(actorEmail)
                        .action(auditable.action())
                        .entityType(auditable.entityType().isEmpty() ? null : auditable.entityType())
                        .entityId(entityId)
                        .outcome(outcome)
                        .errorMessage(errorMessage)
                        .ipAddress(ipAddress)
                        .build());
            } catch (Exception e) {
                log.error("Failed to save audit log for action={}", auditable.action(), e);
            }
        }
    }

    private UUID extractEntityId(ProceedingJoinPoint pjp, String entityIdParam) {
        if (entityIdParam.isEmpty()) {
            return null;
        }
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] paramNames = sig.getParameterNames();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i].equals(entityIdParam) && args[i] instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }

    private UUID extractUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof KivioUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    private String extractRole(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof KivioUserDetails details) {
            return details.getRole();
        }
        return null;
    }

    private String extractEmail(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof KivioUserDetails details) {
            return details.getUsername();
        }
        return null;
    }

    private String getClientIp() {
        // X-Forwarded-For は偽装可能なため使用しない。
        // リバースプロキシ配下では application.yml に server.forward-headers-strategy=NATIVE を設定すること
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(attrs -> attrs.getRequest().getRemoteAddr())
                .orElse(null);
    }
}
