package brooklyn.util.exceptions;

/** marker interface, to show that an exception is suitable for pretty-printing to an end-user,
 * without including a stack trace */
public class UserFacingException extends RuntimeException {

    private static final long serialVersionUID = 2216885527195571323L;

    public UserFacingException(String message) {
        super(message);
    }

    public UserFacingException(Throwable cause) {
        super(cause.getMessage(), cause);
    }

    public UserFacingException(String message, Throwable cause) {
        super(message, cause);
    }

}
