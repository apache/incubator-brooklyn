package brooklyn.event;

/**
 * Indicate an exception when attempting to start an entity.
 * 
 * @deprecated since 0.5, can instead throw any exception; clusters etc should respond accordingly
 */
@Deprecated
public class EntityStartException extends Exception {
    /** serialVersionUID */
    private static final long serialVersionUID = -3496470940340905514L;

    public EntityStartException(String message) {
        super(message);
    }

    public EntityStartException(String message, Throwable cause) {
        super(message, cause);
    }
}