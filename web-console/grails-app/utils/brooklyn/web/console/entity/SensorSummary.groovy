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

    DateFormat formatter = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy ")
    Calendar date = Calendar.getInstance();

    public SensorSummary(Sensor sensor, Object value) {
        this.name = sensor.name
        this.description = sensor.description
        this.value = value
        date.setTimeInMillis(System.currentTimeMillis())
        this.timestamp = formatter.format(date.getTime())

    }

    public SensorSummary(SensorEvent event) {
        this.name = event.sensor.name
        this.description = event.sensor.description
        this.value = event.value
        date.setTimeInMillis(event.timestamp)
        this.timestamp = formatter.format(date.getTime())
    }
}
