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

    /** the flag (key) which should be used to find the value; if empty defaults to field/argument/object name */
    String value() default "";
    
    /** whether the object should not be changed once set; defaults to false
     * <p>
     * this is partially tested for in many routines, but not all;
     * when nullable=false the testing (when done) is guaranteed.
     * however if nullable is allowed we do not distinguish between null and unset
     * so explicitly setting null then setting to a value is not detected as an illegal mutating.
     */
    boolean immutable() default false;
    
    /** whether the object is required & should not be set to null; defaults to true.
     * (there is no 'required' parameter, but setting nullable false then invoking 
     * e.g. {@link FlagUtils#checkRequiredFields(Object)} has the effect of requiring a value.) 
     * <p>
     * this is partially tested for in many routines, but not all
     */
    boolean nullable() default true;

}
