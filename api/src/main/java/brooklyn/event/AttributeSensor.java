package brooklyn.event;

/**
 * The interface implemented by attribute sensors.
 */
public interface AttributeSensor<T> extends Sensor<T> {
    // Marker Interface

    /**
     * Gets the scan period in miliseconds.
     *
     * @return the scan period in miliseconds.
     */
    long getScanPeriodMs()
}
