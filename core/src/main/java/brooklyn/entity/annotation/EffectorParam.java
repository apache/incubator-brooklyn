package brooklyn.entity.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//FIXME Move to brooklyn.entity.effector?

/**
 * Gives meta-data about a parameter of the effector.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface EffectorParam {
    String name();
    String description() default MAGIC_STRING_MEANING_NULL;
    String defaultValue() default MAGIC_STRING_MEANING_NULL;
    boolean nullable() default true;
    
    // Cannot use null as a default (e.g. for defaultValue); therefore define a magic string to mean that
    // so can tell when no-one has set it.
    public static final String MAGIC_STRING_MEANING_NULL = "null default value; do not missuse! 3U=Hhfkr8wuov]WO";
}
