package com.gj4.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = ElementType.TYPE)
public @interface Component {
    String value() default "";

    Scope scope() default Scope.SINGLETON;

    public enum Scope {
        SINGLETON,
        PROTOTYPE
    }
}
