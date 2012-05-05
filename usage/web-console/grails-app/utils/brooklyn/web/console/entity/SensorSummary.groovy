package brooklyn.web.console.entity

import java.text.DateFormat
import java.text.SimpleDateFormat

import brooklyn.event.Sensor
import brooklyn.event.SensorEvent

/**
 */
public class SensorSummary {
    
    // TODO ENGR-1808: want a nicer way to render values; what if it doesn't have a nice toString?
    // Can we move the rendering decision out of here, and into EntityController.sensors
    // When we have a RESTful api then more important we give the raw values
    
    public final String name
    public final String description
    public final String value
    public final String timestamp
    public final String actions

    // formatter is not thread-safe; use thread-local storage
    private static final ThreadLocal<DateFormat> formatter = new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
            SimpleDateFormat result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            result.setTimeZone(TimeZone.getTimeZone("GMT"))
            return result
        }
    };

    public SensorSummary(Sensor sensor, Object value) {
        this.name = sensor.name
        this.description = sensor.description
        this.value = value
        this.timestamp = formatter.get().format(new Date())
        this.actions = [ "open": "http://www.google.com/" ]
    }

    public SensorSummary(SensorEvent event) {
        this.name = event.sensor.name
        this.description = event.sensor.description
        this.value = event.value?.toString()
        this.timestamp = formatter.get().format(new Date(event.timestamp))
    }
}
