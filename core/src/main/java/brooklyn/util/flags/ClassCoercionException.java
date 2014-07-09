package brooklyn.util.flags;

/**
 * Thrown to indicate that {@link TypeCoercions} could not cast an object from one
 * class to another.
 */
public class ClassCoercionException extends ClassCastException {
    public ClassCoercionException() {
        super();
    }

    /**
     * Constructs a <code>ClassCoercionException</code> with the specified
     * detail message.
     *
     * @param s the detail message.
     */
    public ClassCoercionException(String s) {
        super(s);
    }
}
