package io.kivio.domain.identity.exception;

import io.kivio.common.exception.BadRequestException;

/**
 * 登録セッション（registrationToken）無効例外を表現します。
 */
public class RegistrationSessionInvalidException extends BadRequestException {

    public RegistrationSessionInvalidException() {
        super("REGISTRATION_SESSION_INVALID", "登録セッションが無効または期限切れです。登録を最初からやり直してください");
    }
}
