package io.kivio.domain.identity.dto.response;

/**
 * メールアドレス重複チェックレスポンスを表現します。
 */
public record CheckEmailResponse(
        /** 利用可能フラグ */
        boolean available
) {}
