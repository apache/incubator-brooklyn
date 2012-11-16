package brooklyn.rest.apidoc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** like Swagger Api annotation (and treated similarly) but doesn't require path to be repeated, and supports a name */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Apidoc {
    String value();
    String description() default "";
    // ? what is 'open' in @Api
}
