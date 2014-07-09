package brooklyn.location;


/**
 * Indicates no machines are available in a given location.
 */
public class NoMachinesAvailableException extends Exception {
    private static final long serialVersionUID = 1079817235289265761L;
    
    public NoMachinesAvailableException(String s) {
        super(s);
    }

    public NoMachinesAvailableException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
