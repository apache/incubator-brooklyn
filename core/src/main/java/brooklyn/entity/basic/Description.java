package brooklyn.entity.basic;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//FIXME Move to brooklyn.entity.effector?

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
/** 
 * Provides runtime access to the description of an effector or paramter, esp for effectors.
 * 
 * @deprecated since 0.6; use {@link brooklyn.entity.annotation.Effector} annotation instead, with its description member; or use
 *             {@link brooklyn.entity.annotation.EffectorParam} annotation for effector parameters.
 */
@Deprecated
public @interface Description {
    String value();
}

