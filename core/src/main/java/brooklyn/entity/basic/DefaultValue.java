package brooklyn.entity.basic;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// FIXME Move to brooklyn.entity.effector?

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
/** provides runtime access to the name of a paramter, esp for effectors; typically matches any default value supplied in the code */
public @interface DefaultValue {
    String value();
}
