package net.oneki.mtac.core.util.introspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)

public @interface ApiRequestInterface {
    @AliasFor("schema")
    String value() default "";

    @AliasFor("value")
    String schema() default "";

    // String[] permissions() default {};
    // String[] notInheritPermissions() default {};
}
