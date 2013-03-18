package brooklyn.location;


/**
 * Indicates no machines are available in a given location.
 */
public class NoMachinesAvailableException extends Exception {
    private static final long serialVersionUID = 1079817235289265761L;
    
    @Deprecated
    private Location location;

    /** @deprecated since 0.5; always include a helpful message! */
    public NoMachinesAvailableException() {
    }

    public NoMachinesAvailableException(String s) {
        super(s);
    }

    public NoMachinesAvailableException(String s, Throwable throwable) {
        super(s, throwable);
    }

    /** @deprecated since 0.5; always include a helpful message! */
    public NoMachinesAvailableException(Throwable throwable) {
        super(throwable);
    }

    /**
     * @deprecated since 0.5; don't include Location - just use {@link #NoMachinesAvailableException(String)}
     */
    public NoMachinesAvailableException(Location location) {
        super("No machines available in "+location.toString());
        this.location = location;
    }

    /**
     * @deprecated since 0.5; don't include Location - just use {@link #NoMachinesAvailableException(String)}
     */
    public NoMachinesAvailableException(Location location, String s) {
        super(s);
        this.location = location;
    }
    
    /**
     * @deprecated since 0.5; don't include Location - just use {@link #NoMachinesAvailableException(String, Throwable)}
     */
    public NoMachinesAvailableException(Location location, String s, Throwable throwable) {
        super(s, throwable);
        this.location = location;
    }

    /**
     * @deprecated since 0.5; don't include Location - just use {@link #NoMachinesAvailableException(String, Throwable)}
     */
    public NoMachinesAvailableException(Location location, Throwable throwable) {
        super("No machines available in "+location.toString(), throwable);
        this.location = location;
    }

    /**
     * @deprecated since 0.5; the catcher can know which location had no machines based on which
     *             location they asked for; will remove this so that the exception is definitely 
     *             serializable.
     */
    public Location getLocation() {
        return location;
    }
}
