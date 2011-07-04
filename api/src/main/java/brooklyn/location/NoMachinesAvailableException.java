package brooklyn.location;

import brooklyn.location.Location;

/**
 * Indicates no machines are available in a given location.
 */
public class NoMachinesAvailableException extends Exception {
    private Location location;

    public NoMachinesAvailableException() {
    }

    public NoMachinesAvailableException(String s) {
        super(s);
    }

    public NoMachinesAvailableException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public NoMachinesAvailableException(Throwable throwable) {
        super(throwable);
    }

    public NoMachinesAvailableException(Location location) {
        super("No machines available in "+location.toString());
        this.location = location;
    }

    public NoMachinesAvailableException(Location location, String s) {
        super(s);
        this.location = location;
    }

    public NoMachinesAvailableException(Location location, String s, Throwable throwable) {
        super(s, throwable);
        this.location = location;
    }

    public NoMachinesAvailableException(Location location, Throwable throwable) {
        super("No machines available in "+location.toString(), throwable);
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }
}
