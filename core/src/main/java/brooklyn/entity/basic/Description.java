package brooklyn.entity.basic;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//FIXME Move to brooklyn.entity.effector?

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
/** provides runtime access to the description of an effector or paramter, esp for effectors */
public @interface Description {
    String value();
}

