package brooklyn.util.exceptions;

/** Indicates a runtime exception which has been propagated via {@link Exceptions#propagate} */
public class PropagatedRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 3959054308510077172L;

    final boolean causeEmbeddedInMessage;
    
    /** Callers should typically *not* attempt to summarise the cause in the message here; use toString() to get extended information */
    public PropagatedRuntimeException(String message, Throwable cause) {
        super(message, cause);
        causeEmbeddedInMessage = message.endsWith(Exceptions.collapseText(getCause()));
    }

    public PropagatedRuntimeException(String message, Throwable cause, boolean causeEmbeddedInMessage) {
        super(message, cause);
        this.causeEmbeddedInMessage = causeEmbeddedInMessage;
    }

    public PropagatedRuntimeException(Throwable cause) {
        super("" /* do not use default message as that destroys the toString */, cause);
        causeEmbeddedInMessage = false;
    }

    @Override
    public String toString() {
        if (causeEmbeddedInMessage) return super.toString();
        else return super.toString()+": "+Exceptions.collapseText(getCause());
    }
    
    public boolean isCauseEmbeddedInMessage() {
        return causeEmbeddedInMessage;
    }
}
