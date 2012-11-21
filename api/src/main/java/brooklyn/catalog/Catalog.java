package brooklyn.catalog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** annotation which can be placed on the classpath to indicate the class
 * (Application (Template) / Entity / Policy) should be included in the runtime catalog
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = { ElementType.TYPE })
public @interface Catalog {

    String name() default "";
    String description() default "";
    String iconUrl() default "";
    
}
