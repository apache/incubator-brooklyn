package brooklyn.event.adapter

/**
 * TODO javadoc
 */
public interface ValueProvider<T> {
    /**
     * This method is called by an adapter to compute a value for a {@link Sensor}.
     */
    public T compute();
}
