package io.kivio.domain.identity.exception;

import io.kivio.common.exception.BadRequestException;

/**
 * パスワード変更失敗例外を表現します。
 *
 * <p>
 * 現在のパスワード不一致、または Google ログイン専用ユーザー（{@code passwordHash} が null）に対する
 * パスワード変更時に送出します。アカウントの存在情報を漏らさないため理由は区別しません。
 */
public class PasswordChangeFailedException extends BadRequestException {

    public PasswordChangeFailedException() {
        super("PASSWORD_CHANGE_FAILED", "現在のパスワードが正しくありません");
    }
}
