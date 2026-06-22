package io.kivio.domain.identity.exception;

import io.kivio.common.exception.ForbiddenException;

/**
 * ユーザーアカウント無効化例外を表現します。
 */
public class UserDeactivatedException extends ForbiddenException {

    public UserDeactivatedException() {
        super("USER_DEACTIVATED", "このアカウントは無効化されています");
    }
}
