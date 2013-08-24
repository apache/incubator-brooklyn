package brooklyn.util.exceptions;

/** Indicates a runtime exception which has been propagated via {@link Exceptions#propagate} */
public class PropagatedRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 3959054308510077172L;

    /** Callers should typically *not* attempt to summarise the cause in the message here; use toString() to get extended information */
    public PropagatedRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public PropagatedRuntimeException(Throwable cause) {
        super("", cause);
    }

    @Override
    public String toString() {
        return super.toString()+": "+Exceptions.collapseText(getCause());
    }
}
