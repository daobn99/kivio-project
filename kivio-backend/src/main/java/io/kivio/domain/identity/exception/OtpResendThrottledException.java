package io.kivio.domain.identity.exception;

import io.kivio.common.exception.TooManyRequestsException;

/**
 * 認証コード（OTP）再送信スロットリング例外を表現します。
 *
 * <p>同一メールアドレスへの再送信間隔が短すぎる、または 1 時間あたりの送信上限を超えた場合に発生します。
 * IP 単位のレート制限だけではクロス IP のメール爆撃を防げないため、メールアドレス単位でも制限します。
 */
public class OtpResendThrottledException extends TooManyRequestsException {

    public OtpResendThrottledException() {
        super("RATE_LIMIT_EXCEEDED", "認証コードの送信回数が上限に達しました。しばらくしてから再度お試しください");
    }
}
