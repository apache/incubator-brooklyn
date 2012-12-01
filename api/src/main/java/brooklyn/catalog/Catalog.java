package brooklyn.catalog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 
 * annotation that can be placed on an Application (template), entity or policy 
 * to give metadata for when used in a catalog and to indicate inclusion in annotation-scanned catalogs
 * <p>
 * the "id" field used in the catalog is not exposed here but is always taken as the Class.getName() of the annotated item
 * if loaded from an annotation.  (the "type" field unsurprisingly is given the same value).  
 * {@link #name()}, if not supplied, is the SimpleName of the class.
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = { ElementType.TYPE })
public @interface Catalog {

    String name() default "";
    String description() default "";
    String iconUrl() default "";
    
}
