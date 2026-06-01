package io.kivio.domain.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 監査ログ記録を指示するアノテーションを表現します。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();
    String entityType() default "";
    String entityIdParam() default "";
    boolean captureOldValue() default false;
    boolean captureNewValue() default false;
}
