package brooklyn.event;

import java.util.Map;

public class LogSensor<T> extends Sensor<T> {
    public LogSensor(String name, Class<T> type, Map<String, Object> properties) {
        super(name, type, properties);
    }
}