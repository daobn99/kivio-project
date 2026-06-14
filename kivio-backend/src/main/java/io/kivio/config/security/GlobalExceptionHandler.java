package io.kivio.config.security;

import io.kivio.common.exception.KivioException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * グローバル例外ハンドラーを表現します。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    // セキュリティ上の理由から、パスワードやトークンなどの機微情報を含む可能性のあるフィールドは、エラーレスポンスに rejectedValue
    // を含めないようにする
    private static final Set<String> SENSITIVE_FIELDS = Set.of("password", "token", "secret", "credential", "otp");

    @Value("${app.problem-base-url:https://kivio.example.com}")
    private String problemBaseUrl;

    /**
     * KivioException を処理し、RFC 7807 形式のエラーレスポンスを返します。
     * すべての KivioException はこのハンドラーで処理されるため、個別の例外クラスごとにハンドラーを定義する必要はありません。
     */
    @ExceptionHandler(KivioException.class)
    public ProblemDetail handleKivioException(KivioException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setType(URI.create(problemBaseUrl + "/problems/" + toKebabCase(ex.getCode())));
        problem.setTitle(toTitle(ex.getCode()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    /**
     * リクエストの JSON 形式エラーを処理します。 例：不正な JSON、数値に文字列が入っている、など
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "リクエストボディの形式が正しくありません");
        problem.setType(URI.create(problemBaseUrl + "/problems/invalid-request-body"));
        problem.setTitle("Invalid Request Body");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_REQUEST_BODY");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, "リクエストの入力値に不正があります");
        problem.setType(URI.create(problemBaseUrl + "/problems/validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("errors", buildFieldErrors(ex.getBindingResult().getFieldErrors()));
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "認証が必要です");
        problem.setType(URI.create(problemBaseUrl + "/problems/unauthorized"));
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "UNAUTHORIZED");
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "このリソースへのアクセス権がありません");
        problem.setType(URI.create(problemBaseUrl + "/problems/access-denied"));
        problem.setTitle("Access Denied");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ACCESS_DENIED");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("unexpected_error path={} correlationId={}", request.getRequestURI(), MDC.get("correlationId"), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "サーバー内部エラーが発生しました");
        problem.setType(URI.create(problemBaseUrl + "/problems/internal-server-error"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INTERNAL_SERVER_ERROR");
        return problem;
    }

    private List<Map<String, Object>> buildFieldErrors(List<FieldError> errors) {
        return errors.stream().map(e -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("field", e.getField());
            entry.put("message", e.getDefaultMessage());
            if (!isSensitiveField(e.getField())) {
                entry.put("rejectedValue", e.getRejectedValue());
            }
            return entry;
        }).toList();
    }

    private boolean isSensitiveField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return SENSITIVE_FIELDS.stream().anyMatch(lower::contains);
    }

    private String toKebabCase(String upperSnakeCase) {
        return upperSnakeCase.toLowerCase().replace('_', '-');
    }

    private String toTitle(String upperSnakeCase) {
        return Arrays.stream(upperSnakeCase.split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(upperSnakeCase);
    }
}
