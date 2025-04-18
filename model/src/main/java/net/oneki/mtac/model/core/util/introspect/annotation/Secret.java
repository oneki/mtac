package net.oneki.mtac.model.core.util.introspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Secret {
    public enum SecretType {
        ENCRYPTION,
        HASHING
    }

    @AliasFor("type")
    SecretType value() default SecretType.ENCRYPTION;

    @AliasFor("value")
    SecretType type();

}
