package brooklyn.util.exceptions;

/** As {@link FatalRuntimeException} super, specialized for configuration errors. */
public class FatalConfigurationRuntimeException extends FatalRuntimeException {

    private static final long serialVersionUID = -5361951925760434821L;

    public FatalConfigurationRuntimeException(String message) {
        super(message);
    }
    
    public FatalConfigurationRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
