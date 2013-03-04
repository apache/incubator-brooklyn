package brooklyn.event.adapter.legacy

/**
 * TODO javadoc
 * 
 * @deprecated deprecated in 0.4. Use new *SensorAdapter approach.
 */
@Deprecated
public interface ValueProvider<T> {
    /**
     * This method is called by an adapter to compute a value for a {@link Sensor}.
     */
    public T compute();
}
