package brooklyn.entity.proxying;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import brooklyn.entity.Entity;

/**
 * A pointer to the default implementation of an entity.
 *
 * @author aled
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface ImplementedBy {

  /**
   * The implementation type.
   */
  Class<? extends Entity> value();
}
