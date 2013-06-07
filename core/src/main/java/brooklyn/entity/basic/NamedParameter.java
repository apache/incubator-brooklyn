package brooklyn.entity.basic;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//FIXME Move to brooklyn.entity.effector?

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
/**
 * Provides a runtime name of a paramter, esp for effectors; typically matches the name in the code.
 * 
 * @deprecated since 0.6; use {@link brooklyn.entity.annotation.EffectorParam} annotation instead, with its name member.
 */
@Deprecated
public @interface NamedParameter {
    String value();
}
