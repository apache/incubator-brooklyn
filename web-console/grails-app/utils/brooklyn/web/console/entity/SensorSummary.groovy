package brooklyn.web.console.entity

import brooklyn.event.Sensor

/**
 */
public class SensorSummary {
    public final String description
    public final Object value

    public SensorSummary(Sensor sensor, Object value) {
        this.description = sensor.description
        this.value = value
    }
}
