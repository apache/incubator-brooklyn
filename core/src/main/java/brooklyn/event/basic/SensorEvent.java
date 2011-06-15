package brooklyn.event.basic;

import brooklyn.entity.Entity;
import brooklyn.event.Event;
import brooklyn.event.Sensor;

public class SensorEvent<T> implements Event<T> {
	private final Sensor<T> sensor;
	private final Entity source;

    private T value;
	
	public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }

    public Sensor<T> getSensor() { return sensor; }

    public Entity getSource() { return source; }
    
    public SensorEvent(Sensor<T> sensor, Entity source, T value) {
        this.sensor = sensor;
        this.source = source;
        this.value = value;
    }
}
