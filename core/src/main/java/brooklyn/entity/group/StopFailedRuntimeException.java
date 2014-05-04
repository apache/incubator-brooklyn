package brooklyn.entity.group;

/**
 * Indicates that a stop operation has failed - e.g. stopping of an entity
 * when doing {@link DynamicCluster#replaceMember(String)}.
 * 
 * This exception is generally only used when it is necessary to distinguish
 * between different errors - e.g. when replacing a member, did it fail starting
 * the new member or stopping the old member.
 */
public class StopFailedRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 8993327511541890753L;

    public StopFailedRuntimeException(String message) {
        super(message);
    }
    
    public StopFailedRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
