package brooklyn.util.exceptions;

/** Exception indicating a fatal config error, typically used in CLI routines. */
public class FatalConfigurationRuntimeException extends RuntimeException {

    private static final long serialVersionUID = -5361951925760434821L;

    public FatalConfigurationRuntimeException(String message) {
        super(message);
    }
    
    public FatalConfigurationRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
