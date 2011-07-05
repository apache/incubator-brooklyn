package brooklyn.web.console.entity

import brooklyn.event.Sensor

/**
 */
public class SensorSummary {
    public final Sensor sensor
    public final Object value

    public SensorSummary(Sensor sensor, Object value) {
        this.sensor = sensor
        this.value = value
    }
}
