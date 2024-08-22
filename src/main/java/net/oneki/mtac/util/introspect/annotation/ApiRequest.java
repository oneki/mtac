package net.oneki.mtac.util.introspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)

public @interface ApiRequest {

    @AliasFor("schema")
    String value() default "";

    @AliasFor("value")
    String schema() default "";

    String uniqueInScope() default "tenant.root";
}
