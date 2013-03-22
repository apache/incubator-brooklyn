package brooklyn.catalog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = { ElementType.FIELD })
public @interface CatalogConfig {

    /** a label to be displayed when a config key is exposed as editable in the catalog */ 
    String label();
    
    /** a priority used to determine the order in which config keys are displayed when presenting as editable in the catalog;
     * a higher value appears higher in the list. the default is 1.
     * (negative values may be used to indicate advanced config which might not be shown unless requested.) */ 
    double priority() default 1;
    
}
