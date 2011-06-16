package brooklyn.event;


public class LogSensor<T> extends Sensor<T> {
    private static final long serialVersionUID = 4713993465669948212L;

    public LogSensor(String description, String name, Class<T> type) {
        super(description, name, type);
    }
    
    public LogSensor(String name, Class<T> type) {
        super(name, type);
    }
}
