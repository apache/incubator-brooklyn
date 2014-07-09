package brooklyn.util.exceptions;

/** Exception indicating a fatal error, typically used in CLI routines.
 * The message supplied here should be suitable for display in a CLI response (without stack trace / exception class). */
public class FatalRuntimeException extends RuntimeException {

    private static final long serialVersionUID = -3359163414517503809L;

    public FatalRuntimeException(String message) {
        super(message);
    }
    
    public FatalRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
