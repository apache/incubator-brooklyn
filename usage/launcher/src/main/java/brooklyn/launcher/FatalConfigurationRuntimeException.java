package brooklyn.launcher;

public class FatalConfigurationRuntimeException extends RuntimeException {

    private static final long serialVersionUID = -5361951925760434821L;

    public FatalConfigurationRuntimeException(String message) {
        super(message);
    }
    
    public FatalConfigurationRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
