package brooklyn.util.flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation to indicate that a variable may be set through the use of a named argument,
 * looking for the name specified here or inferred from the annotated field/argument/object.
 * <p>
 * This is used to automate the processing where named arguments are passed in constructors
 * and other methods, and the values of those named arguments should be transferred to
 * other known fields/arguments/objects at runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface SetFromFlag {

    String value() default "";
            
}
