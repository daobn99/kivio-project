package io.kivio.common.exception;

import org.springframework.http.HttpStatusCode;

/**
 * アプリケーション例外の基底クラスを表現します。
 */
public abstract class KivioException extends RuntimeException {

    private final String errorCode;
    private final HttpStatusCode status;

    protected KivioException(String errorCode, String message, HttpStatusCode status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
