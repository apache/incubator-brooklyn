package brooklyn.event.basic;

/**
 * A {@link Sensor} describing a log message or exceptional condition.
 */
public class LogSensor<T> extends BasicSensor<T> {
    private static final long serialVersionUID = 4713993465669948212L;

    public LogSensor(Class<T> type, String name) {
        super(type, name, name);
    }
    
    public LogSensor(Class<T> type, String name, String description) {
        super(type, name, description);
    }
}
