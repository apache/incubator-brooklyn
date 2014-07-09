package brooklyn.entity.proxying;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import brooklyn.entity.Entity;

/**
 * A pointer to the default implementation of an entity.
 * 
 * A common naming convention is for the implementation class to have the suffix "Impl",
 * but this is not required.
 * 
 * See {@link EntityTypeRegistry} for how to override the implementation to be used, if
 * the class referenced by this annotation is not desired.
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
