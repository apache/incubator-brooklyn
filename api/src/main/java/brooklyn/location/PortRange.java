package brooklyn.location;

/**
 * A range of ports, where min and max are inclusive.
 */
public interface PortRange {
    /** The minimum port in the range, inclusive. */
    int getMin();
    
    /** The maximum port in the range, inclusive. */
    int getMax();
}
