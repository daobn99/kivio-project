package io.kivio.common.exception;

import org.springframework.http.HttpStatusCode;

public class ResourceNotFoundException extends KivioException {

    public ResourceNotFoundException(String detail) {
        super("RESOURCE_NOT_FOUND", detail, HttpStatusCode.valueOf(404));
    }

    public ResourceNotFoundException(String entityName, Object id) {
        super("RESOURCE_NOT_FOUND",
                "ID '%s' の %s は存在しないか削除されています".formatted(id, entityName),
                HttpStatusCode.valueOf(404));
    }
}
