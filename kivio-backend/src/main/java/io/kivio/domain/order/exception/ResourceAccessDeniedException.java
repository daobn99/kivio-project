package io.kivio.domain.order.exception;

import io.kivio.common.exception.ForbiddenException;

/**
 * 他人のリソースへのアクセス拒否例外（403 {@code ACCESS_DENIED}）を表現します。
 *
 * <p>
 * 所有者でないユーザーが住所の更新・削除を試みた場合に送出します。
 * Spring Security の {@link org.springframework.security.access.AccessDeniedException} とは別物で、
 * {@code KivioException} 階層としてコード/ステータスを保持し {@code GlobalExceptionHandler} が自動描画します。
 */
public class ResourceAccessDeniedException extends ForbiddenException {

    public ResourceAccessDeniedException() {
        super("ACCESS_DENIED", "このリソースへのアクセス権がありません");
    }
}
