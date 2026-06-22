package io.kivio.common.exception;

import org.springframework.http.HttpStatusCode;

/**
 * アプリケーション例外の基底クラスを表現します。
 */
public abstract class KivioException extends RuntimeException {

    private final String code;
    private final HttpStatusCode status;

    protected KivioException(String code, String message, HttpStatusCode status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
