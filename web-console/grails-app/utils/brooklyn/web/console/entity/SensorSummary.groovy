package brooklyn.web.console.entity

import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 */
public class SensorSummary {
    public final String name
    public final String description
    public final Object value
    public final String timestamp

    private static DateFormat formatter = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy ")

    public SensorSummary(Sensor sensor, Object value) {
        this.name = sensor.name
        this.description = sensor.description
        this.value = value
        this.timestamp = formatter.format(new Date())
    }

    public SensorSummary(SensorEvent event) {
        this.name = event.sensor.name
        this.description = event.sensor.description
        this.value = event.value
        this.timestamp = formatter.format(new Date(event.timestamp))
    }
}
