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
     * and even that testing is not (cannot) be perfect because fields have default values:
     * if an object is explicitly set to its default/initial value
     * (i.e. null for an Object, 0 for an int, false for a boolean) 
     * then set to something different, this is not flagged as a violation.
     * (when used with Objects in conjunction with nullable=false,
     * via the FlagUtils routines, it is reliable.)
     */
    boolean immutable() default false;
    
    /** whether the object should not be set to null; defaults to true
     * <p>
     * this is partially tested for in many routines, but not all
     */
    boolean nullable() default true;

}
